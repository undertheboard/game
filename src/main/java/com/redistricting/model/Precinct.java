package com.redistricting.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A precinct is the smallest geographic unit on a redistricting map.
 *
 * <p>Geometry is stored as a list of rings ({@code List<List<double[]>>}) so
 * that MultiPolygon precincts (common in Redistricting Data Hub data — e.g.
 * coastal NC precincts that include islands) are represented faithfully.
 * Each ring is an outer boundary; holes are not modelled.
 */
public final class Precinct {

    private final String id;
    private int district;
    private final int population;
    private final int demVotes;
    private final int repVotes;
    private final List<List<double[]>> rings;

    public Precinct(String id, int district, int population,
                    int demVotes, int repVotes, List<List<double[]>> rings) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("precinct id required");
        }
        if (population < 0 || demVotes < 0 || repVotes < 0) {
            throw new IllegalArgumentException("population/votes must be >= 0");
        }
        if (rings == null || rings.isEmpty()) {
            throw new IllegalArgumentException("at least one polygon ring required");
        }
        List<List<double[]>> copy = new ArrayList<>(rings.size());
        for (List<double[]> ring : rings) {
            if (ring == null || ring.size() < 3) {
                throw new IllegalArgumentException("each ring needs >= 3 vertices");
            }
            copy.add(List.copyOf(ring));
        }
        this.id = id;
        this.district = district;
        this.population = population;
        this.demVotes = demVotes;
        this.repVotes = repVotes;
        this.rings = List.copyOf(copy);
    }

    public String id() { return id; }
    public int district() { return district; }
    public void setDistrict(int d) { this.district = d; }
    public int population() { return population; }
    public int demVotes() { return demVotes; }
    public int repVotes() { return repVotes; }
    public List<List<double[]>> rings() { return rings; }

    /** Convenience for callers that only care about the first/largest ring. */
    public List<double[]> primaryRing() {
        List<double[]> best = rings.get(0);
        double bestArea = ringArea(best);
        for (int i = 1; i < rings.size(); i++) {
            double a = ringArea(rings.get(i));
            if (a > bestArea) { best = rings.get(i); bestArea = a; }
        }
        return best;
    }

    /** Total area summed across all rings (shoelace formula). */
    public double area() {
        double sum = 0.0;
        for (List<double[]> ring : rings) sum += ringArea(ring);
        return sum;
    }

    /** Total perimeter summed across all rings. */
    public double perimeter() {
        double sum = 0.0;
        for (List<double[]> ring : rings) sum += ringPerimeter(ring);
        return sum;
    }

    /** Centroid of the primary ring (good enough for adjacency heuristics). */
    public double[] centroid() {
        List<double[]> ring = primaryRing();
        double cx = 0, cy = 0;
        for (double[] v : ring) { cx += v[0]; cy += v[1]; }
        return new double[] { cx / ring.size(), cy / ring.size() };
    }

    private static double ringArea(List<double[]> ring) {
        double sum = 0.0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            double[] a = ring.get(i);
            double[] b = ring.get((i + 1) % n);
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(sum) * 0.5;
    }

    private static double ringPerimeter(List<double[]> ring) {
        double sum = 0.0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            double[] a = ring.get(i);
            double[] b = ring.get((i + 1) % n);
            sum += Math.hypot(b[0] - a[0], b[1] - a[1]);
        }
        return sum;
    }
}
