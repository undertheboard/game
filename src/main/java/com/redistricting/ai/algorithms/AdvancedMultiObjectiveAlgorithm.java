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
 * <strong>Advanced multi-objective</strong> redistricting algorithm.
 *
 * <p><em>Goal:</em> a high-quality general-purpose plan balancing
 * <strong>all</strong> the objectives the user can configure: population
 * equality, partisan target (from the bias slider), county adherence and
 * geometric compactness.
 *
 * <p><em>Method:</em>
 * <ol>
 *   <li><strong>Multi-attempt growth.</strong> Run
 *       {@link GenerationParams#attempts()} independent attempts. Each
 *       attempt seeds the districts using k-means++ farthest-first sampling
 *       (county-aware when {@code countyAdherence > 0.5}) and grows them
 *       greedily under a weighted cost combining population deviation,
 *       partisan-share deviation, county-split penalty and centroid
 *       distance.</li>
 *   <li><strong>Contiguity repair.</strong> Re-attach any disconnected
 *       island to its strongest neighbouring district.</li>
 *   <li><strong>Boundary local search.</strong> Polish with
 *       {@link BoundaryRefiner}, swapping boundary precincts to greedily
 *       reduce the same multi-objective score, while preserving
 *       contiguity.</li>
 *   <li><strong>Best-of selection.</strong> Keep the attempt with the
 *       lowest combined score, breaking ties on partisan seat-gap.</li>
 * </ol>
 */
public final class AdvancedMultiObjectiveAlgorithm implements RedistrictingAlgorithm {

    @Override public String id() { return "advanced"; }
    @Override public String displayName() { return "Advanced — multi-objective"; }
    @Override public String description() {
        return "Greedy seeded growth with a weighted cost (population, partisan target, "
             + "county adherence, compactness), followed by contiguity repair and a "
             + "boundary local-search polish pass. Runs N attempts and keeps the best.";
    }

    @Override
    public int[] assign(PrecinctBase base, GenerationParams params) {
        Random masterRng = new Random(params.seed());
        int targetDemSeats = targetDemSeats(params.partisanBias(), params.districts());

        int[] bestAssignment = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int bestSeatGap = Integer.MAX_VALUE;

        BoundaryRefiner.Objective obj = makeObjective(base, params, targetDemSeats);

        for (int attempt = 0; attempt < params.attempts(); attempt++) {
            long attemptSeed = masterRng.nextLong();
            int[] assignment = grow(base, params, attemptSeed, targetDemSeats);
            GeographyUtils.repairContiguity(assignment, base.adjacency(),
                    params.districts(), 4);

            BoundaryRefiner.refine(assignment, params.districts(), base.precincts(),
                    base.county(), base.counties(), base.adjacency(),
                    obj, /*passes*/ 8, new Random(attemptSeed ^ 0xCAFE));
            GeographyUtils.repairContiguity(assignment, base.adjacency(),
                    params.districts(), 4);

            BoundaryRefiner.Stats stats = BoundaryRefiner.statsOf(assignment,
                    params.districts(), base.precincts(), base.county(), base.counties());
            double score = obj.score(stats, params.districts());
            int seatGap = Math.abs(countDemSeats(stats, params.districts()) - targetDemSeats);
            if (score < bestScore - 1e-9
                    || (Math.abs(score - bestScore) <= 1e-9 && seatGap < bestSeatGap)) {
                bestScore = score;
                bestSeatGap = seatGap;
                bestAssignment = assignment;
            }
        }
        return bestAssignment;
    }

    // ---------- growth ----------

    private int[] grow(PrecinctBase base, GenerationParams p, long seed, int targetDemSeats) {
        Random rng = new Random(seed);
        List<Precinct> precincts = base.precincts();
        int[][] adj = base.adjacency();
        int[] county = base.county();
        int n = precincts.size();
        int D = p.districts();
        long totalPop = base.totalPopulation();
        double idealPop = (double) totalPop / D;

        int[] assignment = new int[n];
        for (int i = 0; i < n; i++) assignment[i] = -1;

        long[] districtPop = new long[D];
        long[] districtDem = new long[D];
        long[] districtRep = new long[D];
        double[] cxSum = new double[D];
        double[] cySum = new double[D];
        @SuppressWarnings("unchecked")
        Set<Integer>[] districtCounties = new Set[D];
        @SuppressWarnings("unchecked")
        Set<Integer>[] frontier = new Set[D];
        for (int d = 0; d < D; d++) {
            districtCounties[d] = new HashSet<>();
            frontier[d] = new HashSet<>();
        }

        double[] targetDemShare = new double[D];
        for (int d = 0; d < D; d++) {
            targetDemShare[d] = (d < targetDemSeats) ? 0.55 : 0.35;
        }

        int[] seeds = GeographyUtils.kmeansPlusPlusSeeds(D, n,
                Math.max(1, p.precinctsX()), county, p.countyAdherence(), rng);
        for (int d = 0; d < D; d++) {
            int idx = seeds[d];
            assignment[idx] = d;
            recordAdd(d, idx, precincts, county, districtPop, districtDem, districtRep,
                    cxSum, cySum, districtCounties);
            for (int nb : adj[idx]) if (assignment[nb] == -1) frontier[d].add(nb);
        }

        int remaining = n - D;
        while (remaining > 0) {
            int chosenD = -1;
            double minRatio = Double.POSITIVE_INFINITY;
            for (int d = 0; d < D; d++) {
                if (frontier[d].isEmpty()) continue;
                double ratio = districtPop[d] / idealPop;
                if (ratio < minRatio) { minRatio = ratio; chosenD = d; }
            }
            if (chosenD == -1) break;

            int picked = bestCandidate(chosenD, frontier[chosenD], precincts, county,
                    districtPop, districtDem, districtRep, cxSum, cySum,
                    districtCounties, targetDemShare, idealPop, p, n);
            assignment[picked] = chosenD;
            recordAdd(chosenD, picked, precincts, county, districtPop, districtDem,
                    districtRep, cxSum, cySum, districtCounties);
            for (Set<Integer> f : frontier) f.remove(picked);
            for (int nb : adj[picked]) {
                if (assignment[nb] == -1) frontier[chosenD].add(nb);
            }
            remaining--;
        }

        // Mop up orphans.
        for (int i = 0; i < n; i++) {
            if (assignment[i] != -1) continue;
            int d = nearestAssignedDistrict(i, adj, assignment);
            if (d == -1) d = 0;
            assignment[i] = d;
            recordAdd(d, i, precincts, county, districtPop, districtDem, districtRep,
                    cxSum, cySum, districtCounties);
        }
        return assignment;
    }

    private int bestCandidate(int d, Set<Integer> frontier, List<Precinct> precincts,
                              int[] county, long[] districtPop, long[] districtDem,
                              long[] districtRep, double[] cxSum, double[] cySum,
                              Set<Integer>[] districtCounties, double[] targetDemShare,
                              double idealPop, GenerationParams p, int n) {
        int best = -1;
        double bestCost = Double.POSITIVE_INFINITY;
        double cx = districtPop[d] == 0 ? 0 : cxSum[d] / districtPop[d];
        double cy = districtPop[d] == 0 ? 0 : cySum[d] / districtPop[d];
        for (int idx : frontier) {
            Precinct pr = precincts.get(idx);
            long newPop = districtPop[d] + pr.population();
            long newDem = districtDem[d] + pr.demVotes();
            long newRep = districtRep[d] + pr.repVotes();
            double demShare = (newDem + newRep) == 0 ? 0.5
                    : (double) newDem / (newDem + newRep);
            double[] c = pr.centroid();

            double popPenalty = Math.abs(newPop - idealPop) / idealPop;
            double leanPenalty = Math.abs(demShare - targetDemShare[d]);
            double countyPenalty = districtCounties[d].contains(county[idx]) ? 0.0 : 1.0;
            double dist = Math.hypot(c[0] - cx, c[1] - cy) / Math.sqrt(n);

            double cost = popPenalty
                    + 1.5 * leanPenalty
                    + p.countyAdherence() * countyPenalty
                    + p.compactness() * dist;

            if (newPop > idealPop * (1 + p.populationTolerance())
                    && hasUnderfilledOther(d, districtPop, idealPop, p)) {
                cost += 100;
            }
            if (cost < bestCost) { bestCost = cost; best = idx; }
        }
        return best;
    }

    private boolean hasUnderfilledOther(int d, long[] districtPop,
                                        double ideal, GenerationParams p) {
        for (int i = 0; i < districtPop.length; i++) {
            if (i == d) continue;
            if (districtPop[i] < ideal * (1 - p.populationTolerance())) return true;
        }
        return false;
    }

    private void recordAdd(int d, int idx, List<Precinct> precincts, int[] county,
                           long[] districtPop, long[] districtDem, long[] districtRep,
                           double[] cxSum, double[] cySum,
                           Set<Integer>[] districtCounties) {
        Precinct pr = precincts.get(idx);
        double[] c = pr.centroid();
        cxSum[d] += c[0] * pr.population();
        cySum[d] += c[1] * pr.population();
        districtPop[d] += pr.population();
        districtDem[d] += pr.demVotes();
        districtRep[d] += pr.repVotes();
        districtCounties[d].add(county[idx]);
    }

    private static int nearestAssignedDistrict(int start, int[][] adj, int[] assignment) {
        Deque<Integer> q = new ArrayDeque<>();
        boolean[] seen = new boolean[assignment.length];
        q.add(start); seen[start] = true;
        while (!q.isEmpty()) {
            int cur = q.poll();
            for (int nb : adj[cur]) {
                if (assignment[nb] != -1) return assignment[nb];
                if (!seen[nb]) { seen[nb] = true; q.add(nb); }
            }
        }
        return -1;
    }

    // ---------- objective + helpers ----------

    static BoundaryRefiner.Objective makeObjective(PrecinctBase base, GenerationParams p,
                                                    int targetDemSeats) {
        long totalPop = base.totalPopulation();
        final double idealPop = (double) totalPop / p.districts();
        return (s, D) -> {
            double popDev = 0;
            for (int d = 0; d < D; d++) {
                popDev = Math.max(popDev, Math.abs(s.pop[d] - idealPop) / idealPop);
            }
            int demSeats = countDemSeats(s, D);
            double seatGap = Math.abs(demSeats - targetDemSeats) / (double) D;

            double countySplits = 0;
            for (int d = 0; d < D; d++) {
                int touched = 0;
                for (int v : s.countyMembership[d]) if (v > 0) touched++;
                countySplits += Math.max(0, touched - 1);
            }
            countySplits /= Math.max(1, D);

            return popDev
                 + 1.5 * seatGap
                 + p.countyAdherence() * countySplits;
        };
    }

    static int countDemSeats(BoundaryRefiner.Stats s, int D) {
        int n = 0;
        for (int d = 0; d < D; d++) if (s.dem[d] > s.rep[d]) n++;
        return n;
    }

    /** Map a partisan-bias slider value to a target number of Democratic seats. */
    public static int targetDemSeats(int bias, int districts) {
        double frac = 0.5 + (bias / 100.0) * 0.5;
        int seats = (int) Math.round(frac * districts);
        return Math.max(0, Math.min(districts, seats));
    }
}
