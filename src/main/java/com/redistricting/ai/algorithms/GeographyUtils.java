package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;
import com.redistricting.model.Precinct;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Shared low-level building blocks used by every {@link RedistrictingAlgorithm}.
 *
 * <p>This class is deliberately the only place that knows how to:
 * <ul>
 *   <li>Build a synthetic precinct grid from {@link GenerationParams}.</li>
 *   <li>Compute rook-style adjacency over that grid.</li>
 *   <li>Run BFS / connectivity checks needed by every contiguity-preserving
 *       algorithm.</li>
 *   <li>Enforce a final "every district is a single connected component"
 *       guarantee by re-attaching any orphan island to its strongest
 *       neighbouring district.</li>
 * </ul>
 *
 * Centralising these primitives keeps the actual redistricting algorithms
 * focused on <em>strategy</em> rather than on bookkeeping.
 */
public final class GeographyUtils {

    /** Baseline synthetic population per precinct (jittered upward by ≤20%). */
    public static final int BASE_POP_PER_PRECINCT = 1000;

    private GeographyUtils() {}

    // ---------- precinct grid construction --------------------------------

    /**
     * Build a {@code precinctsX × precinctsY} grid of unit-square precincts
     * with synthetic population and partisan splits driven by the requested
     * partisan bias and county layout in {@code params}.
     *
     * <p>Per-precinct Dem share = clamp01(state mean + county jitter +
     * precinct jitter), with the state mean shifted by the bias slider.
     */
    public static List<Precinct> buildPrecincts(GenerationParams p, long seed) {
        Random rng = new Random(seed);
        int nx = p.precinctsX();
        int ny = p.precinctsY();
        int cx = p.countiesX();
        int cy = p.countiesY();

        double stateMean = 0.5 + 0.20 * (p.partisanBias() / 100.0);
        double[][] countyMean = new double[cx][cy];
        for (int i = 0; i < cx; i++) {
            for (int j = 0; j < cy; j++) {
                countyMean[i][j] = clamp01(stateMean + (rng.nextDouble() - 0.5) * 0.30);
            }
        }

        List<Precinct> out = new ArrayList<>(nx * ny);
        for (int y = 0; y < ny; y++) {
            for (int x = 0; x < nx; x++) {
                int cxIdx = Math.min(cx - 1, x * cx / nx);
                int cyIdx = Math.min(cy - 1, y * cy / ny);
                double demShare = clamp01(
                        countyMean[cxIdx][cyIdx] + (rng.nextDouble() - 0.5) * 0.20);

                int pop = BASE_POP_PER_PRECINCT + rng.nextInt(BASE_POP_PER_PRECINCT / 5);
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

    /** County index per precinct (parallel to the precinct list). */
    public static int[] countyOf(GenerationParams p) {
        int nx = p.precinctsX();
        int ny = p.precinctsY();
        int cx = p.countiesX();
        int cy = p.countiesY();
        int[] out = new int[nx * ny];
        for (int i = 0; i < out.length; i++) {
            int x = i % nx;
            int y = i / nx;
            int cxIdx = Math.min(cx - 1, x * cx / nx);
            int cyIdx = Math.min(cy - 1, y * cy / ny);
            out[i] = cyIdx * cx + cxIdx;
        }
        return out;
    }

    /** Rook (4-neighbour) adjacency over a {@code nx × ny} grid. */
    public static int[][] gridAdjacency(int nx, int ny) {
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

    // ---------- BFS / contiguity ------------------------------------------

    /**
     * Returns {@code true} iff removing precinct {@code idx} from district
     * {@code d} would leave the remaining members connected. Used as a
     * cheap "safe to swap" test by the local-search refiner.
     */
    public static boolean wouldStayConnectedWithout(int idx, int[] assignment,
                                                     int[][] adj) {
        int d = assignment[idx];
        int start = -1;
        int count = 0;
        for (int nb : adj[idx]) {
            if (assignment[nb] == d) {
                if (start == -1) start = nb;
                count++;
            }
        }
        if (count == 0) return false;          // would leave district empty of neighbours
        if (count == 1) return true;           // one neighbour remains → trivially fine

        // BFS from `start` over members of d, skipping `idx`.
        Deque<Integer> q = new ArrayDeque<>();
        Set<Integer> seen = new HashSet<>();
        q.add(start);
        seen.add(start);
        int reached = 0;
        while (!q.isEmpty()) {
            int cur = q.poll();
            reached++;
            for (int nb : adj[cur]) {
                if (nb == idx) continue;
                if (assignment[nb] != d) continue;
                if (seen.add(nb)) q.add(nb);
            }
        }
        // Compare to total members minus the one we're removing.
        int total = 0;
        for (int v : assignment) if (v == d) total++;
        return reached == total - 1;
    }

    /**
     * Return the size of every connected component (under {@code adj}) within
     * the precincts assigned to district {@code d}. A district is contiguous
     * iff exactly one component exists.
     */
    public static int[] componentSizes(int d, int[] assignment, int[][] adj) {
        int n = assignment.length;
        boolean[] seen = new boolean[n];
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (assignment[i] != d || seen[i]) continue;
            int size = 0;
            Deque<Integer> q = new ArrayDeque<>();
            q.add(i);
            seen[i] = true;
            while (!q.isEmpty()) {
                int cur = q.poll();
                size++;
                for (int nb : adj[cur]) {
                    if (assignment[nb] == d && !seen[nb]) {
                        seen[nb] = true;
                        q.add(nb);
                    }
                }
            }
            sizes.add(size);
        }
        int[] out = new int[sizes.size()];
        for (int i = 0; i < sizes.size(); i++) out[i] = sizes.get(i);
        return out;
    }

    /**
     * Repair contiguity: for every district that has more than one connected
     * component, the smaller components are re-assigned to whichever
     * neighbouring district they border most. Runs to a fixed point or up
     * to {@code maxPasses} sweeps.
     */
    public static void repairContiguity(int[] assignment, int[][] adj,
                                        int districts, int maxPasses) {
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean changed = false;
            for (int d = 0; d < districts; d++) {
                int[] sizes = componentSizes(d, assignment, adj);
                if (sizes.length <= 1) continue;
                // Find the largest component → keep. All others are reassigned.
                int largestSize = 0;
                int largestStart = -1;
                int n = assignment.length;
                boolean[] seen = new boolean[n];
                List<int[]> components = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    if (assignment[i] != d || seen[i]) continue;
                    List<Integer> bag = new ArrayList<>();
                    Deque<Integer> q = new ArrayDeque<>();
                    q.add(i);
                    seen[i] = true;
                    while (!q.isEmpty()) {
                        int cur = q.poll();
                        bag.add(cur);
                        for (int nb : adj[cur]) {
                            if (assignment[nb] == d && !seen[nb]) {
                                seen[nb] = true;
                                q.add(nb);
                            }
                        }
                    }
                    int[] arr = bag.stream().mapToInt(Integer::intValue).toArray();
                    components.add(arr);
                    if (arr.length > largestSize) {
                        largestSize = arr.length;
                        largestStart = components.size() - 1;
                    }
                }
                for (int c = 0; c < components.size(); c++) {
                    if (c == largestStart) continue;
                    int[] comp = components.get(c);
                    // Vote among bordering districts (other than d).
                    int[] votes = new int[districts];
                    for (int idx : comp) {
                        for (int nb : adj[idx]) {
                            int dn = assignment[nb];
                            if (dn != d) votes[dn]++;
                        }
                    }
                    int target = -1;
                    int bestVotes = -1;
                    for (int t = 0; t < districts; t++) {
                        if (votes[t] > bestVotes) { bestVotes = votes[t]; target = t; }
                    }
                    if (target == -1 || bestVotes == 0) continue; // truly isolated
                    for (int idx : comp) assignment[idx] = target;
                    changed = true;
                }
            }
            if (!changed) return;
        }
    }

    // ---------- seed selection --------------------------------------------

    /**
     * Pick {@code D} seed precincts well-spread across the grid using a
     * k-means++ style farthest-first sampling. {@code countyAdherence} biases
     * the picker toward distinct counties when high.
     */
    public static int[] kmeansPlusPlusSeeds(int D, int n, int nx,
                                             int[] county,
                                             double countyAdherence,
                                             Random rng) {
        int[] seeds = new int[D];
        Set<Integer> usedCounties = new HashSet<>();
        Set<Integer> usedPrecincts = new HashSet<>();

        int first = rng.nextInt(n);
        seeds[0] = first;
        usedPrecincts.add(first);
        usedCounties.add(county[first]);

        double[] dist2 = new double[n];
        Arrays.fill(dist2, Double.POSITIVE_INFINITY);
        for (int i = 0; i < n; i++) {
            dist2[i] = sqDist(i, first, nx);
        }

        for (int s = 1; s < D; s++) {
            // Weighted random pick proportional to dist², biased upward when
            // the candidate is in an unused county.
            double sum = 0;
            double[] w = new double[n];
            for (int i = 0; i < n; i++) {
                if (usedPrecincts.contains(i)) { w[i] = 0; continue; }
                double weight = dist2[i] + 1e-9;
                if (countyAdherence > 0.5 && usedCounties.contains(county[i])) {
                    weight *= (1.0 - countyAdherence);
                }
                w[i] = weight;
                sum += weight;
            }
            double r = rng.nextDouble() * sum;
            int pick = -1;
            double acc = 0;
            for (int i = 0; i < n; i++) {
                acc += w[i];
                if (acc >= r) { pick = i; break; }
            }
            if (pick == -1) {
                // Fallback: any unused index.
                for (int i = 0; i < n; i++) {
                    if (!usedPrecincts.contains(i)) { pick = i; break; }
                }
            }
            seeds[s] = pick;
            usedPrecincts.add(pick);
            usedCounties.add(county[pick]);
            for (int i = 0; i < n; i++) {
                double d2 = sqDist(i, pick, nx);
                if (d2 < dist2[i]) dist2[i] = d2;
            }
        }
        return seeds;
    }

    /** Squared Euclidean distance between two grid indices. */
    public static double sqDist(int a, int b, int nx) {
        int ax = a % nx, ay = a / nx;
        int bx = b % nx, by = b / nx;
        double dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** Render a signed bias as `D+xx`, `R+xx`, or `±0`. */
    public static String signedBias(int v) {
        return (v > 0 ? "D+" : v < 0 ? "R+" : "±") + Math.abs(v);
    }
}
