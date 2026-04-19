package com.redistricting.model;

import java.util.List;

/**
 * A precinct is the smallest geographic unit on a redistricting map.
 * It has a polygon shape, a population, partisan vote counts, and is
 * assigned to exactly one district.
 */
public final class Precinct {

    private final String id;
    private int district;
    private final int population;
    private final int demVotes;
    private final int repVotes;
    private final List<double[]> polygon;

    public Precinct(String id, int district, int population,
                    int demVotes, int repVotes, List<double[]> polygon) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("precinct id required");
        }
        if (population < 0 || demVotes < 0 || repVotes < 0) {
            throw new IllegalArgumentException("population/votes must be >= 0");
        }
        if (polygon == null || polygon.size() < 3) {
            throw new IllegalArgumentException("polygon needs at least 3 vertices");
        }
        this.id = id;
        this.district = district;
        this.population = population;
        this.demVotes = demVotes;
        this.repVotes = repVotes;
        this.polygon = List.copyOf(polygon);
    }

    public String id() { return id; }
    public int district() { return district; }
    public void setDistrict(int d) { this.district = d; }
    public int population() { return population; }
    public int demVotes() { return demVotes; }
    public int repVotes() { return repVotes; }
    public List<double[]> polygon() { return polygon; }

    /** Signed area using the shoelace formula. */
    public double area() {
        double sum = 0.0;
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get((i + 1) % n);
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(sum) * 0.5;
    }

    /** Perimeter of the polygon. */
    public double perimeter() {
        double sum = 0.0;
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get((i + 1) % n);
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            sum += Math.hypot(dx, dy);
        }
        return sum;
    }

    /** Centroid (geometric mean of vertices, good enough for adjacency heuristics). */
    public double[] centroid() {
        double cx = 0, cy = 0;
        for (double[] v : polygon) { cx += v[0]; cy += v[1]; }
        return new double[] { cx / polygon.size(), cy / polygon.size() };
    }
}
