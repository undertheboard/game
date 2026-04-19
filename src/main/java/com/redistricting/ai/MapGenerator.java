package com.redistricting.ai;

import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates a complete synthetic redistricting plan from {@link GenerationParams}.
 *
 * <p>Each generation runs in two phases:
 * <ol>
 *   <li><strong>Geography</strong>: build a {@code precinctsX × precinctsY} grid
 *       of square unit-area precincts, group them into a {@code countiesX ×
 *       countiesY} county grid, give each precinct a per-capita base Dem share
 *       jittered around its county mean (which itself is jittered around a
 *       state mean derived from {@code partisanBias}), and assign synthetic
 *       populations.</li>
 *   <li><strong>Plan</strong>: pick {@code districts} seed precincts (preferring
 *       distinct counties when {@code countyAdherence} is high), then grow each
 *       district by repeatedly adding the boundary precinct that minimises a
 *       cost combining: distance from district population target, distance from
 *       district partisan target (driven by the bias slider), county-split
 *       penalty, and centroid distance (compactness). The generator runs
 *       {@link GenerationParams#attempts()} attempts and keeps the one whose
 *       resulting Dem-seat count is closest to the requested bias.</li>
 * </ol>
 */
public final class MapGenerator {

    private static final int BASE_POP_PER_PRECINCT = 1000;

    public RedistrictingMap generate(GenerationParams params) {
        Random masterRng = new Random(params.seed());
        int targetDemSeats = targetDemSeats(params.partisanBias(), params.districts());

        RedistrictingMap best = null;
        int bestSeatGap = Integer.MAX_VALUE;
        double bestUnfair = Double.POSITIVE_INFINITY;
        FairnessAnalyzer analyzer = new FairnessAnalyzer();

        for (int attempt = 0; attempt < params.attempts(); attempt++) {
            long attemptSeed = masterRng.nextLong();
            List<Precinct> precincts = buildPrecincts(params, attemptSeed);
            int[] county = countyOf(params, precincts);
            int[][] adjacency = gridAdjacency(params.precinctsX(), params.precinctsY());
            assignDistricts(precincts, county, adjacency, params, attemptSeed);
            RedistrictingMap candidate = new RedistrictingMap(
                    "Generated Plan (bias " + signed(params.partisanBias()) + ")",
                    params.districts(), precincts);
            int demSeats = countDemSeats(candidate);
            int gap = Math.abs(demSeats - targetDemSeats);
            double unfair = analyzer.analyze(candidate).unfairnessScore();
            // Primary: hit the requested bias. Tie-break: prefer fairer geometry.
            if (gap < bestSeatGap || (gap == bestSeatGap && unfair < bestUnfair)) {
                bestSeatGap = gap;
                bestUnfair = unfair;
                best = candidate;
            }
        }
        return best;
    }

    // --- Phase 1: geography -------------------------------------------------

    private List<Precinct> buildPrecincts(GenerationParams p, long seed) {
        Random rng = new Random(seed);
        int nx = p.precinctsX();
        int ny = p.precinctsY();
        int cx = p.countiesX();
        int cy = p.countiesY();

        // Per-county mean Dem share, jittered around a state mean that reflects bias.
        // bias = 0 → 50/50 state. bias = +100 → ~70% Dem state. bias = -100 → ~30% Dem.
        double stateMean = 0.5 + 0.20 * (p.partisanBias() / 100.0);
        double[][] countyMean = new double[cx][cy];
        for (int i = 0; i < cx; i++)
            for (int j = 0; j < cy; j++)
                countyMean[i][j] = clamp01(stateMean + (rng.nextDouble() - 0.5) * 0.30);

        List<Precinct> out = new ArrayList<>(nx * ny);
        for (int y = 0; y < ny; y++) {
            for (int x = 0; x < nx; x++) {
                int cxIdx = Math.min(cx - 1, x * cx / nx);
                int cyIdx = Math.min(cy - 1, y * cy / ny);
                double demShare = clamp01(
                        countyMean[cxIdx][cyIdx] + (rng.nextDouble() - 0.5) * 0.20);

                int pop = BASE_POP_PER_PRECINCT
                        + rng.nextInt(BASE_POP_PER_PRECINCT / 5);
                int dem = (int) Math.round(pop * 0.55 * demShare);
                int rep = (int) Math.round(pop * 0.55 * (1.0 - demShare));

                List<double[]> ring = List.of(
                        new double[] { x, y },
                        new double[] { x + 1, y },
                        new double[] { x + 1, y + 1 },
                        new double[] { x, y + 1 });
                String id = "P" + x + "_" + y;
                out.add(new Precinct(id, 0, pop, dem, rep, List.of(ring)));
            }
        }
        return out;
    }

    /** Returns county index per precinct (parallel to the precinct list). */
    private int[] countyOf(GenerationParams p, List<Precinct> precincts) {
        int nx = p.precinctsX();
        int cx = p.countiesX();
        int cy = p.countiesY();
        int[] out = new int[precincts.size()];
        for (int i = 0; i < precincts.size(); i++) {
            int x = i % nx;
            int y = i / nx;
            int cxIdx = Math.min(cx - 1, x * cx / nx);
            int cyIdx = Math.min(cy - 1, y * cy / p.precinctsY());
            out[i] = cyIdx * cx + cxIdx;
        }
        return out;
    }

    // --- Phase 2: plan assignment ------------------------------------------

    private void assignDistricts(List<Precinct> precincts, int[] county,
                                 int[][] adjacency, GenerationParams p, long seed) {
        Random rng = new Random(seed);
        int n = precincts.size();
        int D = p.districts();
        int totalPop = 0;
        for (Precinct pr : precincts) totalPop += pr.population();
        double idealPop = (double) totalPop / D;

        int[] assignment = new int[n];
        for (int i = 0; i < n; i++) assignment[i] = -1;

        // Per-district running stats.
        int[] districtPop = new int[D];
        int[] districtDem = new int[D];
        int[] districtRep = new int[D];
        double[] centroidX = new double[D];
        double[] centroidY = new double[D];
        @SuppressWarnings("unchecked")
        Set<Integer>[] districtCounties = new Set[D];
        @SuppressWarnings("unchecked")
        Set<Integer>[] frontier = new Set[D];
        for (int d = 0; d < D; d++) {
            districtCounties[d] = new HashSet<>();
            frontier[d] = new HashSet<>();
        }

        // Targets: which districts the bias wants Dems to win.
        int demSeatsTarget = targetDemSeats(p.partisanBias(), D);
        double[] targetDemShare = new double[D];
        for (int d = 0; d < D; d++) {
            // Winning Dem districts aim a bit above 50%; losing ones get packed low.
            targetDemShare[d] = (d < demSeatsTarget) ? 0.55 : 0.35;
        }

        // Seed selection: spread across counties when adherence is high.
        int[] seeds = pickSeeds(D, county, p.countyAdherence(), rng, n);
        for (int d = 0; d < D; d++) {
            int idx = seeds[d];
            assignment[idx] = d;
            recordAdd(d, idx, precincts, county,
                    districtPop, districtDem, districtRep,
                    centroidX, centroidY, districtCounties);
            for (int nb : adjacency[idx]) {
                if (assignment[nb] == -1) frontier[d].add(nb);
            }
        }

        int remaining = n - D;
        while (remaining > 0) {
            // Pick the most "behind" district (smallest pop / ideal ratio).
            int chosenD = -1;
            double minRatio = Double.POSITIVE_INFINITY;
            for (int d = 0; d < D; d++) {
                if (frontier[d].isEmpty()) continue;
                double ratio = districtPop[d] / idealPop;
                if (ratio < minRatio) { minRatio = ratio; chosenD = d; }
            }
            if (chosenD == -1) break; // No district has reachable unassigned land.

            int picked = bestCandidate(chosenD, frontier[chosenD], precincts, county,
                    assignment, districtPop, districtDem, districtRep,
                    centroidX, centroidY, districtCounties,
                    targetDemShare, idealPop, p);
            assignment[picked] = chosenD;
            recordAdd(chosenD, picked, precincts, county,
                    districtPop, districtDem, districtRep,
                    centroidX, centroidY, districtCounties);
            for (Set<Integer> f : frontier) f.remove(picked);
            for (int nb : adjacency[picked]) {
                if (assignment[nb] == -1) frontier[chosenD].add(nb);
            }
            remaining--;
        }

        // Mop up any orphans (rare): give them to the smallest neighbour district
        // they can reach via BFS through assigned land.
        for (int i = 0; i < n; i++) {
            if (assignment[i] != -1) continue;
            int d = nearestDistrict(i, adjacency, assignment, districtPop);
            if (d == -1) d = 0;
            assignment[i] = d;
            recordAdd(d, i, precincts, county,
                    districtPop, districtDem, districtRep,
                    centroidX, centroidY, districtCounties);
        }

        for (int i = 0; i < n; i++) precincts.get(i).setDistrict(assignment[i]);
    }

    private int[] pickSeeds(int D, int[] county, double countyAdherence,
                            Random rng, int n) {
        int[] seeds = new int[D];
        Set<Integer> usedCounties = new HashSet<>();
        Set<Integer> usedPrecincts = new HashSet<>();
        for (int d = 0; d < D; d++) {
            int best = -1;
            for (int trial = 0; trial < 30; trial++) {
                int candidate = rng.nextInt(n);
                if (usedPrecincts.contains(candidate)) continue;
                if (countyAdherence > 0.5 && usedCounties.contains(county[candidate])) continue;
                best = candidate;
                break;
            }
            if (best == -1) {
                do { best = rng.nextInt(n); } while (usedPrecincts.contains(best));
            }
            seeds[d] = best;
            usedPrecincts.add(best);
            usedCounties.add(county[best]);
        }
        return seeds;
    }

    private int bestCandidate(int d, Set<Integer> frontier, List<Precinct> precincts,
                              int[] county, int[] assignment,
                              int[] districtPop, int[] districtDem, int[] districtRep,
                              double[] centroidX, double[] centroidY,
                              Set<Integer>[] districtCounties,
                              double[] targetDemShare, double idealPop,
                              GenerationParams p) {
        int best = -1;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int idx : frontier) {
            Precinct pr = precincts.get(idx);
            int newPop = districtPop[d] + pr.population();
            int newDem = districtDem[d] + pr.demVotes();
            int newRep = districtRep[d] + pr.repVotes();
            double demShare = (newDem + newRep) == 0 ? 0.5
                    : (double) newDem / (newDem + newRep);
            double[] c = pr.centroid();

            double popPenalty = Math.abs(newPop - idealPop) / idealPop;
            double leanPenalty = Math.abs(demShare - targetDemShare[d]);
            double countyPenalty = districtCounties[d].contains(county[idx]) ? 0.0 : 1.0;
            double n = (double) (assignment.length); // never zero
            double cx = districtCounties[d].isEmpty() ? c[0] : centroidX[d] / districtPop[d];
            double cy = districtCounties[d].isEmpty() ? c[1] : centroidY[d] / districtPop[d];
            double dist = Math.hypot(c[0] - cx, c[1] - cy) / Math.sqrt(n);

            double cost = popPenalty
                    + 1.5 * leanPenalty
                    + p.countyAdherence() * countyPenalty
                    + p.compactness() * dist;

            // Hard guard: refuse to overshoot beyond population tolerance when
            // some other district is still under-target.
            if (newPop > idealPop * (1 + p.populationTolerance())
                    && hasUnderfilledOther(d, districtPop, idealPop, p)) {
                cost += 100;
            }
            if (cost < bestCost) { bestCost = cost; best = idx; }
        }
        return best;
    }

    private boolean hasUnderfilledOther(int d, int[] districtPop,
                                        double ideal, GenerationParams p) {
        for (int i = 0; i < districtPop.length; i++) {
            if (i == d) continue;
            if (districtPop[i] < ideal * (1 - p.populationTolerance())) return true;
        }
        return false;
    }

    private void recordAdd(int d, int idx, List<Precinct> precincts, int[] county,
                           int[] districtPop, int[] districtDem, int[] districtRep,
                           double[] centroidX, double[] centroidY,
                           Set<Integer>[] districtCounties) {
        Precinct pr = precincts.get(idx);
        double[] c = pr.centroid();
        // Population-weighted centroid, stored as sum-of-weighted-coords.
        centroidX[d] += c[0] * pr.population();
        centroidY[d] += c[1] * pr.population();
        districtPop[d] += pr.population();
        districtDem[d] += pr.demVotes();
        districtRep[d] += pr.repVotes();
        districtCounties[d].add(county[idx]);
    }

    private int nearestDistrict(int start, int[][] adj, int[] assignment, int[] pop) {
        Deque<Integer> q = new ArrayDeque<>();
        Set<Integer> seen = new HashSet<>();
        q.add(start); seen.add(start);
        while (!q.isEmpty()) {
            int cur = q.poll();
            for (int nb : adj[cur]) {
                if (assignment[nb] != -1) return assignment[nb];
                if (seen.add(nb)) q.add(nb);
            }
        }
        // No assigned neighbour reachable — pick smallest district overall.
        int best = 0;
        for (int i = 1; i < pop.length; i++) if (pop[i] < pop[best]) best = i;
        return best;
    }

    // --- Helpers -----------------------------------------------------------

    /**
     * Map a partisan-bias slider value to a target number of Democratic seats.
     * <ul>
     *   <li>bias = 0 → ⌊D/2⌋ Dem seats (proportional)</li>
     *   <li>bias = +100 → all D seats Dem</li>
     *   <li>bias = -100 → 0 Dem seats</li>
     * </ul>
     */
    static int targetDemSeats(int bias, int districts) {
        double frac = 0.5 + (bias / 100.0) * 0.5;
        int seats = (int) Math.round(frac * districts);
        return Math.max(0, Math.min(districts, seats));
    }

    private static int countDemSeats(RedistrictingMap map) {
        int n = 0;
        for (var d : map.districts()) if (d.winner() == 0) n++;
        return n;
    }

    private static int[][] gridAdjacency(int nx, int ny) {
        int[][] adj = new int[nx * ny][];
        List<Integer> tmp = new ArrayList<>(4);
        for (int y = 0; y < ny; y++) {
            for (int x = 0; x < nx; x++) {
                int idx = y * nx + x;
                tmp.clear();
                if (x > 0)        tmp.add(idx - 1);
                if (x < nx - 1)   tmp.add(idx + 1);
                if (y > 0)        tmp.add(idx - nx);
                if (y < ny - 1)   tmp.add(idx + nx);
                adj[idx] = tmp.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        return adj;
    }

    private static String signed(int v) {
        return (v > 0 ? "D+" : v < 0 ? "R+" : "±") + Math.abs(v);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
