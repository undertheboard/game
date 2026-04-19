package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * <strong>Compactness-focused</strong> redistricting algorithm.
 *
 * <p><em>Goal:</em> produce maps with maximally compact (round-ish) districts.
 * This is the algorithm to choose when geometric quality matters more than
 * partisan or county fidelity — e.g. demonstration plans, court-friendly
 * maps, or "good government" baselines.
 *
 * <p><em>Method:</em> a population-weighted Lloyd-style (k-means)
 * assignment, then a contiguity repair, then a boundary local-search pass
 * that explicitly minimises the average centroid moment.
 *
 * <ol>
 *   <li>Pick {@code D} initial seeds with k-means++ farthest-first sampling.</li>
 *   <li>Lloyd iteration: assign each precinct to the nearest seed weighted by
 *       its population (so seeds drift toward population centres), then
 *       recompute seeds as the population-weighted centroid of their assigned
 *       precincts. Repeat for up to 20 iterations or until stable.</li>
 *   <li>Population balancing: enforce the population tolerance by greedy
 *       transfers of boundary precincts from the most over-populated to the
 *       most under-populated district, keeping contiguity.</li>
 *   <li>Contiguity repair + boundary refinement minimising the
 *       {@code popDev + meanCentroidMoment} objective.</li>
 * </ol>
 */
public final class CompactnessAlgorithm implements RedistrictingAlgorithm {

    @Override public String id() { return "compactness"; }
    @Override public String displayName() { return "Compactness — Lloyd / k-means"; }
    @Override public String description() {
        return "Population-weighted k-means assignment producing geometrically compact "
             + "districts, then population balancing and a boundary refinement pass. "
             + "Best for compactness; ignores partisan goals.";
    }

    @Override
    public RedistrictingMap generate(GenerationParams params) {
        Random masterRng = new Random(params.seed());

        int[] bestAssignment = null;
        List<Precinct> bestPrecincts = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int attempt = 0; attempt < params.attempts(); attempt++) {
            long attemptSeed = masterRng.nextLong();
            List<Precinct> precincts = GeographyUtils.buildPrecincts(params, attemptSeed);
            int[] county = GeographyUtils.countyOf(params);
            int counties = params.countiesX() * params.countiesY();
            int[][] adj = GeographyUtils.gridAdjacency(params.precinctsX(), params.precinctsY());

            int[] assignment = lloyd(precincts, params, attemptSeed, county);
            GeographyUtils.repairContiguity(assignment, adj, params.districts(), 4);
            balancePopulations(assignment, precincts, adj, params);
            GeographyUtils.repairContiguity(assignment, adj, params.districts(), 4);

            BoundaryRefiner.Objective obj = compactnessObjective(precincts, params);
            BoundaryRefiner.refine(assignment, params.districts(), precincts, county,
                    counties, adj, obj, /*passes*/ 8, new Random(attemptSeed ^ 0xC0DE));
            GeographyUtils.repairContiguity(assignment, adj, params.districts(), 4);

            BoundaryRefiner.Stats finalStats = BoundaryRefiner.statsOf(
                    assignment, params.districts(), precincts, county, counties);
            double score = obj.score(finalStats, params.districts());
            if (score < bestScore) {
                bestScore = score;
                bestAssignment = assignment;
                bestPrecincts = precincts;
            }
        }
        return AdvancedMultiObjectiveAlgorithm.materialise(
                bestPrecincts, bestAssignment, params,
                "Generated Plan (Compactness, Lloyd)");
    }

    // ---------- Lloyd iterations ----------

    private int[] lloyd(List<Precinct> precincts, GenerationParams p,
                        long seed, int[] county) {
        Random rng = new Random(seed);
        int n = precincts.size();
        int D = p.districts();
        int nx = p.precinctsX();

        int[] seeds = GeographyUtils.kmeansPlusPlusSeeds(D, n, nx, county, 0.0, rng);
        double[] sx = new double[D];
        double[] sy = new double[D];
        for (int d = 0; d < D; d++) {
            double[] c = precincts.get(seeds[d]).centroid();
            sx[d] = c[0]; sy[d] = c[1];
        }

        int[] assignment = new int[n];
        for (int iter = 0; iter < 20; iter++) {
            // Assign each precinct to the nearest seed.
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                double[] c = precincts.get(i).centroid();
                int best = 0;
                double bestD = Double.POSITIVE_INFINITY;
                for (int d = 0; d < D; d++) {
                    double dx = c[0] - sx[d], dy = c[1] - sy[d];
                    double dist = dx * dx + dy * dy;
                    if (dist < bestD) { bestD = dist; best = d; }
                }
                if (assignment[i] != best) { assignment[i] = best; changed = true; }
            }
            // Recompute seeds as population-weighted centroids.
            double[] newSx = new double[D];
            double[] newSy = new double[D];
            long[] newPop = new long[D];
            for (int i = 0; i < n; i++) {
                Precinct pr = precincts.get(i);
                int d = assignment[i];
                double[] c = pr.centroid();
                newSx[d] += c[0] * pr.population();
                newSy[d] += c[1] * pr.population();
                newPop[d] += pr.population();
            }
            for (int d = 0; d < D; d++) {
                if (newPop[d] > 0) { sx[d] = newSx[d] / newPop[d]; sy[d] = newSy[d] / newPop[d]; }
            }
            if (!changed) break;
        }

        // Guard against any empty district (rare with k-means++ seeding).
        ensureAllDistrictsNonEmpty(assignment, D);
        return assignment;
    }

    private void ensureAllDistrictsNonEmpty(int[] assignment, int D) {
        boolean[] used = new boolean[D];
        for (int v : assignment) used[v] = true;
        for (int d = 0; d < D; d++) {
            if (used[d]) continue;
            // Donate one precinct from the largest district.
            int[] count = new int[D];
            for (int v : assignment) count[v]++;
            int big = 0;
            for (int i = 1; i < D; i++) if (count[i] > count[big]) big = i;
            for (int i = 0; i < assignment.length; i++) {
                if (assignment[i] == big) { assignment[i] = d; break; }
            }
        }
    }

    // ---------- population balancing ----------

    private void balancePopulations(int[] assignment, List<Precinct> precincts,
                                     int[][] adj, GenerationParams p) {
        int D = p.districts();
        long total = 0;
        for (Precinct pr : precincts) total += pr.population();
        double ideal = (double) total / D;
        double tol = Math.max(0.005, p.populationTolerance()); // small floor to converge

        for (int pass = 0; pass < 200; pass++) {
            long[] pop = new long[D];
            for (int i = 0; i < precincts.size(); i++) {
                pop[assignment[i]] += precincts.get(i).population();
            }
            int over = -1, under = -1;
            double maxOver = 0, maxUnder = 0;
            for (int d = 0; d < D; d++) {
                double dev = (pop[d] - ideal) / ideal;
                if (dev > maxOver) { maxOver = dev; over = d; }
                if (-dev > maxUnder) { maxUnder = -dev; under = d; }
            }
            if (over == -1 || under == -1) return;
            if (maxOver <= tol && maxUnder <= tol) return;

            // Find a precinct on the boundary between `over` and `under`
            // (or between `over` and any neighbour, transitively bringing
            // population toward `under`). Prefer direct over→under transfers
            // because they don't create new imbalances.
            int donate = pickDonor(assignment, adj, over, under, precincts);
            if (donate == -1) {
                // Fall back: donate to any neighbour district to ease pressure.
                donate = pickAnyDonor(assignment, adj, over, precincts);
                if (donate == -1) return;
                int target = anyNeighbourDistrict(assignment, adj, donate);
                if (target == -1 || target == over) return;
                if (!GeographyUtils.wouldStayConnectedWithout(donate, assignment, adj)) continue;
                if (countAssigned(assignment, over) <= 1) return;
                assignment[donate] = target;
            } else {
                if (!GeographyUtils.wouldStayConnectedWithout(donate, assignment, adj)) continue;
                if (countAssigned(assignment, over) <= 1) return;
                assignment[donate] = under;
            }
        }
    }

    private int pickDonor(int[] assignment, int[][] adj, int from, int to,
                          List<Precinct> precincts) {
        int best = -1;
        int bestPop = Integer.MAX_VALUE;
        for (int i = 0; i < assignment.length; i++) {
            if (assignment[i] != from) continue;
            boolean touchesTo = false;
            for (int nb : adj[i]) if (assignment[nb] == to) { touchesTo = true; break; }
            if (!touchesTo) continue;
            int pop = precincts.get(i).population();
            if (pop < bestPop) { bestPop = pop; best = i; }
        }
        return best;
    }

    private int pickAnyDonor(int[] assignment, int[][] adj, int from,
                             List<Precinct> precincts) {
        int best = -1;
        int bestPop = Integer.MAX_VALUE;
        for (int i = 0; i < assignment.length; i++) {
            if (assignment[i] != from) continue;
            boolean boundary = false;
            for (int nb : adj[i]) if (assignment[nb] != from) { boundary = true; break; }
            if (!boundary) continue;
            int pop = precincts.get(i).population();
            if (pop < bestPop) { bestPop = pop; best = i; }
        }
        return best;
    }

    private int anyNeighbourDistrict(int[] assignment, int[][] adj, int idx) {
        int self = assignment[idx];
        for (int nb : adj[idx]) if (assignment[nb] != self) return assignment[nb];
        return -1;
    }

    private int countAssigned(int[] assignment, int d) {
        int n = 0;
        for (int v : assignment) if (v == d) n++;
        return n;
    }

    // ---------- objective ----------

    private BoundaryRefiner.Objective compactnessObjective(List<Precinct> precincts,
                                                            GenerationParams p) {
        long total = 0;
        for (Precinct pr : precincts) total += pr.population();
        final double ideal = (double) total / p.districts();

        // Pre-cache precinct centroids and populations for moment computation.
        final int n = precincts.size();
        final double[][] centroid = new double[n][];
        final int[] pop = new int[n];
        for (int i = 0; i < n; i++) {
            centroid[i] = precincts.get(i).centroid();
            pop[i] = precincts.get(i).population();
        }
        return (s, D) -> {
            double popDev = 0;
            for (int d = 0; d < D; d++) {
                popDev = Math.max(popDev, Math.abs(s.pop[d] - ideal) / ideal);
            }
            // Mean intra-district squared centroid distance (lower = more compact).
            // Computed per-call from the cached precinct centroids by summing
            // sq-dist to each district's centroid, weighted by population.
            // Stats doesn't carry per-precinct info, so we approximate using
            // the moment formulation E[||x − μ||²]·pop = Σ pop·||x||² − pop·||μ||².
            // Σpop·||x||² is a constant w.r.t. assignment, but Σ ||cxSum||²/pop
            // does change as boundaries shift.
            double inertia = 0;
            for (int d = 0; d < D; d++) {
                if (s.pop[d] == 0) continue;
                double mux = s.cxSum[d] / s.pop[d];
                double muy = s.cySum[d] / s.pop[d];
                inertia += (mux * mux + muy * muy) * s.pop[d]; // Σ pop·||μ||²
            }
            // Smaller Σpop·||μ||² (more spread centroids) → lower inertia term;
            // we want higher Σpop·||μ||² to mean tighter clusters around their
            // centroid. The classical k-means objective is Σ pop·||x − μ||²
            // = const − Σ pop·||μ||². So minimising −Σ pop·||μ||² = maximise
            // Σ pop·||μ||² is what compactness wants.
            return popDev * 2.0 - inertia / (ideal * Math.max(1, n));
        };
    }
}
