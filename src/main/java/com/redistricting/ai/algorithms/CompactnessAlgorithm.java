package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;
import com.redistricting.model.Precinct;

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
 * assignment, then a contiguity repair, a population-balancing pass, and a
 * boundary local-search pass that explicitly rewards higher Σpop·||μ||²
 * (equivalent, by the König–Huygens identity, to lower within-cluster
 * scatter — i.e. tighter districts).
 *
 * <ol>
 *   <li>Pick {@code D} initial seeds with k-means++ farthest-first sampling.</li>
 *   <li>Lloyd iteration: assign each precinct to the nearest seed, then
 *       recompute seeds as the population-weighted centroid of their
 *       assigned precincts. Repeat for up to 20 iterations or until stable.</li>
 *   <li>Population balancing: enforce the population tolerance with greedy
 *       transfers of small boundary precincts from over- to under-populated
 *       districts, keeping contiguity.</li>
 *   <li>Contiguity repair + boundary refinement.</li>
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
    public int[] assign(PrecinctBase base, GenerationParams params) {
        Random masterRng = new Random(params.seed());

        int[] best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        BoundaryRefiner.Objective obj = compactnessObjective(base, params);

        for (int attempt = 0; attempt < params.attempts(); attempt++) {
            long attemptSeed = masterRng.nextLong();
            int[] assignment = lloyd(base, params, attemptSeed);
            GeographyUtils.repairContiguity(assignment, base.adjacency(),
                    params.districts(), 4);
            balancePopulations(assignment, base, params);
            GeographyUtils.repairContiguity(assignment, base.adjacency(),
                    params.districts(), 4);

            BoundaryRefiner.refine(assignment, params.districts(), base.precincts(),
                    base.county(), base.counties(), base.adjacency(), obj,
                    /*passes*/ 8, new Random(attemptSeed ^ 0xC0DE));
            GeographyUtils.repairContiguity(assignment, base.adjacency(),
                    params.districts(), 4);

            BoundaryRefiner.Stats stats = BoundaryRefiner.statsOf(assignment,
                    params.districts(), base.precincts(), base.county(), base.counties());
            double score = obj.score(stats, params.districts());
            if (score < bestScore) { bestScore = score; best = assignment; }
        }
        return best;
    }

    // ---------- Lloyd iterations ----------

    private int[] lloyd(PrecinctBase base, GenerationParams p, long seed) {
        Random rng = new Random(seed);
        List<Precinct> precincts = base.precincts();
        int n = precincts.size();
        int D = p.districts();

        int[] seeds = GeographyUtils.kmeansPlusPlusSeeds(D, n,
                Math.max(1, p.precinctsX()), base.county(), 0.0, rng);
        double[] sx = new double[D];
        double[] sy = new double[D];
        for (int d = 0; d < D; d++) {
            double[] c = precincts.get(seeds[d]).centroid();
            sx[d] = c[0]; sy[d] = c[1];
        }

        int[] assignment = new int[n];
        for (int iter = 0; iter < 20; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                double[] c = precincts.get(i).centroid();
                int bestD = 0;
                double bestDist = Double.POSITIVE_INFINITY;
                for (int d = 0; d < D; d++) {
                    double dx = c[0] - sx[d], dy = c[1] - sy[d];
                    double dist = dx * dx + dy * dy;
                    if (dist < bestDist) { bestDist = dist; bestD = d; }
                }
                if (assignment[i] != bestD) { assignment[i] = bestD; changed = true; }
            }
            // Recompute seeds.
            double[] nsx = new double[D];
            double[] nsy = new double[D];
            long[] np = new long[D];
            for (int i = 0; i < n; i++) {
                Precinct pr = precincts.get(i);
                int d = assignment[i];
                double[] c = pr.centroid();
                nsx[d] += c[0] * pr.population();
                nsy[d] += c[1] * pr.population();
                np[d]  += pr.population();
            }
            for (int d = 0; d < D; d++) {
                if (np[d] > 0) { sx[d] = nsx[d] / np[d]; sy[d] = nsy[d] / np[d]; }
            }
            if (!changed) break;
        }
        ensureAllDistrictsNonEmpty(assignment, D);
        return assignment;
    }

    private void ensureAllDistrictsNonEmpty(int[] assignment, int D) {
        boolean[] used = new boolean[D];
        for (int v : assignment) used[v] = true;
        for (int d = 0; d < D; d++) {
            if (used[d]) continue;
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

    private void balancePopulations(int[] assignment, PrecinctBase base,
                                     GenerationParams p) {
        int D = p.districts();
        List<Precinct> precincts = base.precincts();
        int[][] adj = base.adjacency();
        long total = base.totalPopulation();
        double ideal = (double) total / D;
        double tol = Math.max(0.005, p.populationTolerance());

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

            int donate = pickDonor(assignment, adj, over, under, precincts);
            if (donate == -1) return;
            if (countAssigned(assignment, over) <= 1) return;
            if (!GeographyUtils.wouldStayConnectedWithout(donate, assignment, adj)) {
                // try a different boundary precinct
                int alt = pickDonorIgnoring(assignment, adj, over, under, precincts, donate);
                if (alt == -1) return;
                if (!GeographyUtils.wouldStayConnectedWithout(alt, assignment, adj)) return;
                donate = alt;
            }
            assignment[donate] = under;
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

    private int pickDonorIgnoring(int[] assignment, int[][] adj, int from, int to,
                                  List<Precinct> precincts, int skip) {
        int best = -1;
        int bestPop = Integer.MAX_VALUE;
        for (int i = 0; i < assignment.length; i++) {
            if (i == skip) continue;
            if (assignment[i] != from) continue;
            boolean touchesTo = false;
            for (int nb : adj[i]) if (assignment[nb] == to) { touchesTo = true; break; }
            if (!touchesTo) continue;
            int pop = precincts.get(i).population();
            if (pop < bestPop) { bestPop = pop; best = i; }
        }
        return best;
    }

    private int countAssigned(int[] assignment, int d) {
        int n = 0;
        for (int v : assignment) if (v == d) n++;
        return n;
    }

    // ---------- objective ----------

    private BoundaryRefiner.Objective compactnessObjective(PrecinctBase base,
                                                            GenerationParams p) {
        long total = base.totalPopulation();
        final double ideal = (double) total / p.districts();
        final int n = base.precincts().size();
        return (s, D) -> {
            double popDev = 0;
            for (int d = 0; d < D; d++) {
                popDev = Math.max(popDev, Math.abs(s.pop[d] - ideal) / ideal);
            }
            // König–Huygens: minimising scatter ⇔ maximising Σ pop·||μ||².
            // We negate that term so this whole expression is "lower-is-better".
            double inertia = 0;
            for (int d = 0; d < D; d++) {
                if (s.pop[d] == 0) continue;
                double mux = s.cxSum[d] / s.pop[d];
                double muy = s.cySum[d] / s.pop[d];
                inertia += (mux * mux + muy * muy) * s.pop[d];
            }
            return popDev * 2.0 - inertia / (Math.max(1.0, ideal) * Math.max(1, n));
        };
    }
}
