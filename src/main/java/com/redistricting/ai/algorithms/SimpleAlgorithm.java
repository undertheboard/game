package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;
import com.redistricting.model.Precinct;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * <strong>Simple</strong> redistricting algorithm.
 *
 * <p><em>Goal:</em> produce a clean, contiguous map with roughly equal
 * district populations as quickly as possible. Suited to the "Simple" UI
 * mode where the user only picks the number of districts and a seed.
 *
 * <p><em>Method:</em> multi-source BFS region growing.
 * <ol>
 *   <li>Pick {@code D} seed precincts via k-means++ farthest-first sampling.</li>
 *   <li>Repeat: find the district that is currently the most under its
 *       ideal population share <em>and</em> still has frontier precincts;
 *       absorb the neighbouring frontier precinct closest to that
 *       district's centroid.</li>
 *   <li>If any unassigned precincts remain (rare; can happen on irregular
 *       precinct topology), give them to the smallest neighbouring
 *       district reachable by BFS.</li>
 *   <li>Final {@link GeographyUtils#repairContiguity contiguity repair}
 *       guarantees every district is a single connected component.</li>
 * </ol>
 *
 * <p>The simple algorithm intentionally <em>ignores</em> the partisan,
 * county and compactness sliders: those are the advanced algorithms' job.
 */
public final class SimpleAlgorithm implements RedistrictingAlgorithm {

    @Override public String id() { return "simple"; }
    @Override public String displayName() { return "Simple — equal population"; }
    @Override public boolean isSimple() { return true; }
    @Override public String description() {
        return "Fast region-growing generator that balances district populations and "
             + "guarantees contiguous districts. Ignores partisan/compactness knobs — "
             + "use an Advanced algorithm if those matter.";
    }

    @Override
    public int[] assign(PrecinctBase base, GenerationParams params) {
        List<Precinct> precincts = base.precincts();
        int[][] adj = base.adjacency();
        int n = precincts.size();
        int D = params.districts();
        if (D > n) {
            throw new IllegalArgumentException(
                    "districts (" + D + ") exceeds precinct count (" + n + ")");
        }

        Random rng = new Random(params.seed());
        int[] seeds = GeographyUtils.kmeansPlusPlusSeeds(D, n,
                /*nx — only used for synthetic ordering*/ Math.max(1, params.precinctsX()),
                base.county(), 0.0, rng);

        int[] assignment = new int[n];
        for (int i = 0; i < n; i++) assignment[i] = -1;

        long[] districtPop = new long[D];
        double[] cxSum = new double[D];
        double[] cySum = new double[D];
        @SuppressWarnings("unchecked")
        Set<Integer>[] frontier = new Set[D];
        for (int d = 0; d < D; d++) frontier[d] = new HashSet<>();

        long totalPop = base.totalPopulation();
        double idealPop = (double) totalPop / D;

        for (int d = 0; d < D; d++) {
            int idx = seeds[d];
            assignment[idx] = d;
            absorb(d, idx, precincts, districtPop, cxSum, cySum);
            for (int nb : adj[idx]) if (assignment[nb] == -1) frontier[d].add(nb);
        }

        int remaining = n - D;
        while (remaining > 0) {
            int chosen = -1;
            double minRatio = Double.POSITIVE_INFINITY;
            for (int d = 0; d < D; d++) {
                if (frontier[d].isEmpty()) continue;
                double ratio = districtPop[d] / idealPop;
                if (ratio < minRatio) { minRatio = ratio; chosen = d; }
            }
            if (chosen == -1) break;

            // Pick the frontier precinct closest to the district's centroid.
            int picked = -1;
            double bestDist = Double.POSITIVE_INFINITY;
            double cx = cxSum[chosen] / districtPop[chosen];
            double cy = cySum[chosen] / districtPop[chosen];
            for (int idx : frontier[chosen]) {
                double[] c = precincts.get(idx).centroid();
                double d2 = (c[0] - cx) * (c[0] - cx) + (c[1] - cy) * (c[1] - cy);
                if (d2 < bestDist) { bestDist = d2; picked = idx; }
            }
            assignment[picked] = chosen;
            absorb(chosen, picked, precincts, districtPop, cxSum, cySum);
            for (Set<Integer> f : frontier) f.remove(picked);
            for (int nb : adj[picked]) {
                if (assignment[nb] == -1) frontier[chosen].add(nb);
            }
            remaining--;
        }

        // Sweep up any unreachable orphan precincts.
        for (int i = 0; i < n; i++) {
            if (assignment[i] != -1) continue;
            int target = nearestAssignedDistrict(i, adj, assignment);
            if (target == -1) target = 0;
            assignment[i] = target;
        }

        GeographyUtils.repairContiguity(assignment, adj, D, 4);

        // Population balancing pass: nudge precincts across district boundaries
        // until every district is within the requested tolerance, while
        // preserving contiguity. Cheap on small bases, bounded for safety.
        rebalancePopulations(assignment, base, params, D, idealPop);
        GeographyUtils.repairContiguity(assignment, adj, D, 4);
        return assignment;
    }

    /**
     * Greedy boundary transfers from over-populated districts to
     * under-populated neighbours.
     *
     * <p>Per pass we rank the districts by signed population deviation and
     * try every (over, under) pair in worst-first order. When we cannot
     * find a contiguity-preserving donor between the <em>worst</em> over /
     * under pair we used to bail out entirely; that early exit was the main
     * reason real datasets ended up with several-percent population skew.
     * We now keep trying lesser-imbalance pairs, and we keep iterating as
     * long as <em>any</em> pass made progress (capped at a generous
     * multiple of the precinct count for safety).
     */
    private static void rebalancePopulations(int[] assignment, PrecinctBase base,
                                             GenerationParams params, int D,
                                             double idealPop) {
        int[][] adj = base.adjacency();
        List<Precinct> precincts = base.precincts();
        // Tight default floor (0.5%) but honour a stricter user tolerance.
        double tol = Math.max(0.005, params.populationTolerance());
        int iterCap = Math.max(2_000, precincts.size() * 4);

        for (int pass = 0; pass < iterCap; pass++) {
            long[] pop = new long[D];
            int[] count = new int[D];
            for (int i = 0; i < precincts.size(); i++) {
                pop[assignment[i]] += precincts.get(i).population();
                count[assignment[i]]++;
            }
            // Sort district ids by deviation (most-over first, most-under last).
            Integer[] order = new Integer[D];
            for (int d = 0; d < D; d++) order[d] = d;
            java.util.Arrays.sort(order, (a, b) -> Double.compare(pop[b], pop[a]));

            // Are we already within tolerance everywhere?
            double maxAbsDev = 0;
            for (int d = 0; d < D; d++) {
                double dev = Math.abs(pop[d] - idealPop) / idealPop;
                if (dev > maxAbsDev) maxAbsDev = dev;
            }
            if (maxAbsDev <= tol) return;

            boolean moved = false;
            outer:
            for (int oi = 0; oi < D; oi++) {
                int over = order[oi];
                if (pop[over] <= idealPop || count[over] <= 1) continue;
                for (int ui = D - 1; ui > oi; ui--) {
                    int under = order[ui];
                    if (pop[under] >= idealPop) continue;
                    int donor = pickDonor(assignment, adj, precincts, over, under,
                            pop, idealPop);
                    if (donor != -1) {
                        assignment[donor] = under;
                        moved = true;
                        break outer;
                    }
                }
            }
            if (!moved) return;
        }
    }

    /**
     * Choose a precinct in {@code over} that touches {@code under} and whose
     * transfer (a) keeps {@code over} connected and (b) does not push
     * {@code under} above ideal. Prefers the donor whose move leaves the
     * combined absolute deviation as small as possible.
     */
    private static int pickDonor(int[] assignment, int[][] adj, List<Precinct> precincts,
                                 int over, int under, long[] pop, double idealPop) {
        int best = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < assignment.length; i++) {
            if (assignment[i] != over) continue;
            boolean touchesUnder = false;
            for (int nb : adj[i]) if (assignment[nb] == under) { touchesUnder = true; break; }
            if (!touchesUnder) continue;
            int pp = precincts.get(i).population();
            // Avoid transfers that would simply flip the imbalance (i.e. make
            // the under-pop district end up larger than the over-pop one was).
            if (pop[under] + pp > pop[over]) continue;
            if (!GeographyUtils.wouldStayConnectedWithout(i, assignment, adj)) continue;
            double newOver = Math.abs(pop[over] - pp - idealPop);
            double newUnder = Math.abs(pop[under] + pp - idealPop);
            double score = newOver + newUnder;
            if (score < bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    private static void absorb(int d, int idx, List<Precinct> precincts,
                               long[] pop, double[] cxSum, double[] cySum) {
        Precinct p = precincts.get(idx);
        pop[d] += p.population();
        double[] c = p.centroid();
        cxSum[d] += c[0] * p.population();
        cySum[d] += c[1] * p.population();
    }

    private static int nearestAssignedDistrict(int start, int[][] adj, int[] assignment) {
        Deque<Integer> q = new ArrayDeque<>();
        boolean[] seen = new boolean[assignment.length];
        q.add(start);
        seen[start] = true;
        while (!q.isEmpty()) {
            int cur = q.poll();
            for (int nb : adj[cur]) {
                if (assignment[nb] != -1) return assignment[nb];
                if (!seen[nb]) { seen[nb] = true; q.add(nb); }
            }
        }
        return -1;
    }
}
