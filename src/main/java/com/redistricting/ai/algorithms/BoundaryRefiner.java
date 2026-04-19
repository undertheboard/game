package com.redistricting.ai.algorithms;

import com.redistricting.model.Precinct;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Boundary-precinct local search refiner.
 *
 * <p>Repeatedly considers boundary precincts and moves each to a neighbouring
 * district when doing so improves the algorithm-specific objective and
 * preserves contiguity of both the source and target districts. Used by the
 * advanced algorithms as a polish pass after the initial growth phase.
 *
 * <p>The refiner is generic in the objective: callers pass a
 * {@link Objective} that, given the current per-district stats, returns a
 * scalar "lower-is-better" value.
 */
public final class BoundaryRefiner {

    /** Per-district running stats — populated and updated by the refiner. */
    public static final class Stats {
        public final int[] pop;
        public final int[] dem;
        public final int[] rep;
        public final int[][] countyMembership; // [district][countyId] = #precincts in that county
        public final double[] cxSum; // weighted by population
        public final double[] cySum;

        public Stats(int districts, int counties) {
            this.pop = new int[districts];
            this.dem = new int[districts];
            this.rep = new int[districts];
            this.countyMembership = new int[districts][counties];
            this.cxSum = new double[districts];
            this.cySum = new double[districts];
        }

        public double centroidX(int d) { return pop[d] == 0 ? 0 : cxSum[d] / pop[d]; }
        public double centroidY(int d) { return pop[d] == 0 ? 0 : cySum[d] / pop[d]; }
    }

    /** Lower-is-better total objective over the current district stats. */
    @FunctionalInterface
    public interface Objective {
        double score(Stats stats, int districts);
    }

    private BoundaryRefiner() {}

    /**
     * Build {@link Stats} from a current assignment.
     */
    public static Stats statsOf(int[] assignment, int districts,
                                List<Precinct> precincts, int[] county,
                                int counties) {
        Stats s = new Stats(districts, counties);
        for (int i = 0; i < precincts.size(); i++) {
            int d = assignment[i];
            Precinct p = precincts.get(i);
            s.pop[d] += p.population();
            s.dem[d] += p.demVotes();
            s.rep[d] += p.repVotes();
            s.countyMembership[d][county[i]]++;
            double[] c = p.centroid();
            s.cxSum[d] += c[0] * p.population();
            s.cySum[d] += c[1] * p.population();
        }
        return s;
    }

    /**
     * Run up to {@code maxPasses} sweeps over all boundary precincts. Each
     * sweep visits the boundary in randomised order (using {@code rng}) and
     * commits any move that strictly improves {@code obj} while keeping both
     * affected districts contiguous and the source district non-empty.
     *
     * @return the number of moves committed.
     */
    public static int refine(int[] assignment, int districts,
                             List<Precinct> precincts, int[] county,
                             int counties, int[][] adj, Objective obj,
                             int maxPasses, Random rng) {
        Stats stats = statsOf(assignment, districts, precincts, county, counties);
        int totalMoves = 0;
        double currentScore = obj.score(stats, districts);

        for (int pass = 0; pass < maxPasses; pass++) {
            // Build the boundary list (precincts with at least one neighbour
            // in a different district).
            int n = assignment.length;
            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i;
            shuffle(order, rng);

            int passMoves = 0;
            for (int i : order) {
                int d = assignment[i];
                Set<Integer> targets = new HashSet<>();
                for (int nb : adj[i]) {
                    if (assignment[nb] != d) targets.add(assignment[nb]);
                }
                if (targets.isEmpty()) continue;
                // Don't empty a district.
                int sourceCount = countAssigned(assignment, d);
                if (sourceCount <= 1) continue;
                // Don't break contiguity of the source.
                if (!GeographyUtils.wouldStayConnectedWithout(i, assignment, adj)) continue;

                Precinct p = precincts.get(i);
                double bestGain = 0;
                int bestTarget = -1;
                for (int t : targets) {
                    apply(stats, d, t, p, county[i], +1);
                    double trial = obj.score(stats, districts);
                    double gain = currentScore - trial;
                    apply(stats, d, t, p, county[i], -1); // revert
                    if (gain > bestGain + 1e-12) {
                        bestGain = gain;
                        bestTarget = t;
                    }
                }
                if (bestTarget != -1) {
                    apply(stats, d, bestTarget, p, county[i], +1);
                    assignment[i] = bestTarget;
                    currentScore -= bestGain;
                    passMoves++;
                }
            }
            totalMoves += passMoves;
            if (passMoves == 0) break; // converged
        }
        return totalMoves;
    }

    private static void shuffle(int[] arr, Random rng) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }
    }

    private static int countAssigned(int[] assignment, int d) {
        int n = 0;
        for (int v : assignment) if (v == d) n++;
        return n;
    }

    private static void apply(Stats s, int from, int to, Precinct p,
                              int county, int sign) {
        if (sign > 0) {
            s.pop[from] -= p.population(); s.pop[to] += p.population();
            s.dem[from] -= p.demVotes();   s.dem[to] += p.demVotes();
            s.rep[from] -= p.repVotes();   s.rep[to] += p.repVotes();
            s.countyMembership[from][county]--;
            s.countyMembership[to][county]++;
            double[] c = p.centroid();
            s.cxSum[from] -= c[0] * p.population();
            s.cySum[from] -= c[1] * p.population();
            s.cxSum[to]   += c[0] * p.population();
            s.cySum[to]   += c[1] * p.population();
        } else {
            // reverse of the +1 path
            s.pop[from] += p.population(); s.pop[to] -= p.population();
            s.dem[from] += p.demVotes();   s.dem[to] -= p.demVotes();
            s.rep[from] += p.repVotes();   s.rep[to] -= p.repVotes();
            s.countyMembership[from][county]++;
            s.countyMembership[to][county]--;
            double[] c = p.centroid();
            s.cxSum[from] += c[0] * p.population();
            s.cySum[from] += c[1] * p.population();
            s.cxSum[to]   -= c[0] * p.population();
            s.cySum[to]   -= c[1] * p.population();
        }
    }
}
