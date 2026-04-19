package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * <strong>Simple</strong> redistricting algorithm.
 *
 * <p><em>Goal:</em> produce a clean, contiguous map with roughly equal
 * district populations as quickly as possible, using only the few knobs a
 * casual user cares about (district count, grid size, seed).
 *
 * <p><em>Method:</em> multi-source BFS region growing.
 * <ol>
 *   <li>Pick {@code D} seed precincts via k-means++ farthest-first sampling
 *       so seeds are well spread across the grid.</li>
 *   <li>Repeat: find the district that is currently the most under its
 *       ideal population share <em>and</em> still has frontier precincts;
 *       absorb the neighbouring frontier precinct closest to that
 *       district's centroid.</li>
 *   <li>If any unassigned precincts remain (e.g. an island created by a
 *       narrow corridor), give them to the smallest neighbouring district
 *       reachable by BFS.</li>
 *   <li>Final {@link GeographyUtils#repairContiguity contiguity repair}
 *       pass guarantees every district is a single connected component.</li>
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
    public RedistrictingMap generate(GenerationParams params) {
        long seed = params.seed();
        List<Precinct> precincts = GeographyUtils.buildPrecincts(params, seed);
        int[] county = GeographyUtils.countyOf(params);
        int[][] adj = GeographyUtils.gridAdjacency(params.precinctsX(), params.precinctsY());

        int n = precincts.size();
        int D = params.districts();
        int[] assignment = new int[n];
        for (int i = 0; i < n; i++) assignment[i] = -1;

        Random rng = new Random(seed);
        int[] seeds = GeographyUtils.kmeansPlusPlusSeeds(D, n, params.precinctsX(),
                county, /*countyAdherence*/ 0.0, rng);

        int[] districtPop = new int[D];
        double[] cxSum = new double[D];
        double[] cySum = new double[D];
        @SuppressWarnings("unchecked")
        Set<Integer>[] frontier = new Set[D];
        for (int d = 0; d < D; d++) frontier[d] = new HashSet<>();

        long totalPop = 0;
        for (Precinct p : precincts) totalPop += p.population();
        double idealPop = (double) totalPop / D;

        for (int d = 0; d < D; d++) {
            int idx = seeds[d];
            assignment[idx] = d;
            Precinct p = precincts.get(idx);
            districtPop[d] += p.population();
            double[] c = p.centroid();
            cxSum[d] += c[0] * p.population();
            cySum[d] += c[1] * p.population();
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
            Precinct p = precincts.get(picked);
            districtPop[chosen] += p.population();
            double[] c = p.centroid();
            cxSum[chosen] += c[0] * p.population();
            cySum[chosen] += c[1] * p.population();
            for (Set<Integer> f : frontier) f.remove(picked);
            for (int nb : adj[picked]) {
                if (assignment[nb] == -1) frontier[chosen].add(nb);
            }
            remaining--;
        }

        // Sweep up any orphan precincts.
        for (int i = 0; i < n; i++) {
            if (assignment[i] != -1) continue;
            int target = nearestAssignedDistrict(i, adj, assignment);
            if (target == -1) target = 0;
            assignment[i] = target;
        }

        GeographyUtils.repairContiguity(assignment, adj, D, 4);

        // Apply assignments to precincts.
        List<Precinct> finalPrecincts = new ArrayList<>(precincts.size());
        for (int i = 0; i < precincts.size(); i++) {
            Precinct old = precincts.get(i);
            Precinct copy = new Precinct(old.id(), assignment[i], old.population(),
                    old.demVotes(), old.repVotes(), old.rings());
            finalPrecincts.add(copy);
        }
        return new RedistrictingMap(
                "Generated Plan (Simple, equal-pop)", D, finalPrecincts);
    }

    private static int nearestAssignedDistrict(int start, int[][] adj, int[] assignment) {
        java.util.Deque<Integer> q = new java.util.ArrayDeque<>();
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
