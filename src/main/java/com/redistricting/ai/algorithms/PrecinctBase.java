package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The geographic substrate every {@link RedistrictingAlgorithm} runs on:
 * a fixed set of precincts plus their rook-style adjacency graph and an
 * (optional) per-precinct county index.
 *
 * <p>All redistricting algorithms in this package operate on a
 * {@code PrecinctBase} rather than building their own geometry, so the same
 * algorithm can be used on:
 * <ul>
 *   <li>real precinct-level data imported from the Redistricting Data Hub
 *       (or any compatible precinct GeoJSON), or</li>
 *   <li>a synthetic grid base generated from {@link GenerationParams} for
 *       demos and tests.</li>
 * </ul>
 *
 * <p>Adjacency built from real precincts uses shared polygon vertices as a
 * proxy for shared edges — coarse but effective for any topology that came
 * out of a real GIS pipeline.
 */
public final class PrecinctBase {

    private final List<Precinct> precincts;
    private final int[][] adjacency;
    private final int[] county;     // size == precincts.size(); 0 when unknown
    private final int counties;     // total distinct county ids
    private final String label;

    public PrecinctBase(List<Precinct> precincts, int[][] adjacency,
                        int[] county, int counties, String label) {
        if (precincts == null || precincts.isEmpty()) {
            throw new IllegalArgumentException("precincts required");
        }
        if (adjacency == null || adjacency.length != precincts.size()) {
            throw new IllegalArgumentException("adjacency size mismatch");
        }
        if (county == null || county.length != precincts.size()) {
            throw new IllegalArgumentException("county size mismatch");
        }
        this.precincts = List.copyOf(precincts);
        this.adjacency = adjacency;
        this.county = county;
        this.counties = Math.max(1, counties);
        this.label = label == null ? "precincts" : label;
    }

    public List<Precinct> precincts() { return precincts; }
    public int[][] adjacency() { return adjacency; }
    public int[] county() { return county; }
    public int counties() { return counties; }
    public int size() { return precincts.size(); }
    public String label() { return label; }

    public long totalPopulation() {
        long sum = 0;
        for (Precinct p : precincts) sum += p.population();
        return sum;
    }

    // -------- factories ---------------------------------------------------

    /**
     * Build a base from any imported {@link RedistrictingMap}, computing
     * adjacency by polygon-vertex sharing (rook contiguity within a small
     * tolerance). The original district assignment carried by the map is
     * <em>ignored</em> — the base is just the geographic substrate.
     */
    public static PrecinctBase fromMap(RedistrictingMap map) {
        List<Precinct> ps = map.precincts();
        int[][] adj = computeVertexAdjacency(ps);
        // Treat each precinct as its own county when no county info is known.
        int[] county = new int[ps.size()];
        for (int i = 0; i < ps.size(); i++) county[i] = i;
        return new PrecinctBase(ps, adj, county, ps.size(), map.name());
    }

    /**
     * Build a synthetic precinct grid base from generation params. Used by
     * the bundled demo path and by tests so the algorithms always have
     * <em>something</em> to run on.
     */
    public static PrecinctBase synthetic(GenerationParams params) {
        List<Precinct> ps = GeographyUtils.buildPrecincts(params, params.seed());
        int[][] adj = GeographyUtils.gridAdjacency(params.precinctsX(), params.precinctsY());
        int[] county = GeographyUtils.countyOf(params);
        int counties = params.countiesX() * params.countiesY();
        return new PrecinctBase(ps, adj, county, counties, "synthetic " +
                params.precinctsX() + "x" + params.precinctsY());
    }

    // -------- adjacency construction --------------------------------------

    /**
     * Compute precinct adjacency by hashing polygon vertices into a coarse
     * spatial grid: any two precincts that share a vertex (within the
     * tolerance) are considered neighbours. This is the same heuristic used
     * by {@code FairnessAnalyzer} but returned as an {@code int[][]} indexed
     * by precinct list position for the algorithms.
     */
    private static int[][] computeVertexAdjacency(List<Precinct> precincts) {
        double tol = 1e-6;
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < precincts.size(); i++) {
            for (List<double[]> ring : precincts.get(i).rings()) {
                for (double[] v : ring) {
                    long key = cellKey(v[0], v[1], tol);
                    grid.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
                }
            }
        }
        // Build adjacency sets.
        @SuppressWarnings("unchecked")
        java.util.Set<Integer>[] sets = new java.util.HashSet[precincts.size()];
        for (int i = 0; i < sets.length; i++) sets[i] = new java.util.HashSet<>();
        for (List<Integer> bucket : grid.values()) {
            int n = bucket.size();
            if (n < 2) continue;
            for (int a = 0; a < n; a++) {
                int ia = bucket.get(a);
                for (int b = a + 1; b < n; b++) {
                    int ib = bucket.get(b);
                    if (ia == ib) continue;
                    sets[ia].add(ib);
                    sets[ib].add(ia);
                }
            }
        }
        int[][] adj = new int[precincts.size()][];
        for (int i = 0; i < adj.length; i++) {
            int[] arr = sets[i].stream().mapToInt(Integer::intValue).toArray();
            Arrays.sort(arr);
            adj[i] = arr;
        }
        return adj;
    }

    private static long cellKey(double x, double y, double tol) {
        long ix = Math.round(x / tol);
        long iy = Math.round(y / tol);
        return (ix * 73856093L) ^ (iy * 19349663L);
    }
}
