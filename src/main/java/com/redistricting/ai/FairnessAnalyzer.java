package com.redistricting.ai;

import com.redistricting.model.District;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The "AI" half of the app. Combines three classical redistricting fairness
 * metrics into a single unfairness score and provides a hill-climbing
 * optimizer that swaps boundary precincts between districts to lower it.
 *
 * <p>Metrics:
 * <ul>
 *   <li><strong>Population deviation</strong> — max |pop − ideal| / ideal
 *       across districts. 0 means perfectly equal districts.</li>
 *   <li><strong>Compactness</strong> — mean Polsby-Popper score (4πA / P²)
 *       across districts. 1.0 = circle, 0 = degenerate sliver.</li>
 *   <li><strong>Efficiency gap</strong> — (wastedR − wastedD) / totalVotes,
 *       per Stephanopoulos & McGhee. ±0 = balanced; |gap| ≥ 0.07 is the
 *       common gerrymandering threshold.</li>
 * </ul>
 * The combined unfairness is
 * {@code popDev + (1 − avgCompactness) + 2·|efficiencyGap|}.
 */
public final class FairnessAnalyzer {

    private final double popWeight;
    private final double compactnessWeight;
    private final double efficiencyWeight;

    public FairnessAnalyzer() {
        this(1.0, 1.0, 2.0);
    }

    public FairnessAnalyzer(double popWeight, double compactnessWeight,
                            double efficiencyWeight) {
        this.popWeight = popWeight;
        this.compactnessWeight = compactnessWeight;
        this.efficiencyWeight = efficiencyWeight;
    }

    public FairnessReport analyze(RedistrictingMap map) {
        List<District> districts = map.districts();
        int totalPop = map.totalPopulation();
        double ideal = (double) totalPop / map.districtCount();

        double maxDev = 0;
        double compactnessSum = 0;
        int compactnessCount = 0;
        for (District d : districts) {
            double dev = Math.abs(d.totalPopulation() - ideal) / Math.max(1, ideal);
            if (dev > maxDev) maxDev = dev;

            double area = d.totalArea();
            double perim = districtPerimeter(d);
            if (area > 0 && perim > 0) {
                compactnessSum += 4 * Math.PI * area / (perim * perim);
                compactnessCount++;
            }
        }
        double avgCompact = compactnessCount > 0 ? compactnessSum / compactnessCount : 0;
        double eg = efficiencyGap(districts);

        double score = popWeight * maxDev
                + compactnessWeight * (1.0 - avgCompact)
                + efficiencyWeight * Math.abs(eg);

        return new FairnessReport(maxDev, avgCompact, eg, score, districts);
    }

    /**
     * Hill-climbing optimizer: repeatedly moves a boundary precinct to a
     * neighbouring district when doing so lowers the unfairness score.
     * Mutates the precinct → district assignments on {@code map}.
     *
     * @return the report after optimization (may equal the starting report
     *         if no improving moves were found, or if the plan has only one
     *         precinct per district — common for DRA "District Shapes" data).
     */
    public FairnessReport optimize(RedistrictingMap map, int maxIterations, long seed) {
        Random rng = new Random(seed);
        FairnessReport best = analyze(map);

        // Only meaningful when at least one district holds multiple precincts.
        boolean canOptimize = map.precincts().size() > map.districtCount();
        if (!canOptimize) return best;

        Map<String, List<String>> adjacency = buildAdjacency(map);
        Map<String, Precinct> byId = new HashMap<>();
        for (Precinct p : map.precincts()) byId.put(p.id(), p);

        for (int iter = 0; iter < maxIterations; iter++) {
            // Pick a random precinct on a district boundary.
            List<Precinct> precincts = map.precincts();
            Precinct candidate = precincts.get(rng.nextInt(precincts.size()));
            List<String> neighbours = adjacency.getOrDefault(candidate.id(), List.of());
            Set<Integer> otherDistricts = new HashSet<>();
            for (String nid : neighbours) {
                Precinct n = byId.get(nid);
                if (n != null && n.district() != candidate.district()) {
                    otherDistricts.add(n.district());
                }
            }
            if (otherDistricts.isEmpty()) continue;

            int originalDistrict = candidate.district();
            int sourceCount = countInDistrict(precincts, originalDistrict);
            if (sourceCount <= 1) continue; // Don't empty a district.

            int newDistrict = pickRandom(otherDistricts, rng);
            candidate.setDistrict(newDistrict);
            FairnessReport trial = analyze(map);
            if (trial.unfairnessScore() + 1e-12 < best.unfairnessScore()) {
                best = trial;
            } else {
                candidate.setDistrict(originalDistrict); // Revert.
            }
        }
        return best;
    }

    private static int countInDistrict(List<Precinct> precincts, int district) {
        int n = 0;
        for (Precinct p : precincts) if (p.district() == district) n++;
        return n;
    }

    private static int pickRandom(Set<Integer> set, Random rng) {
        int idx = rng.nextInt(set.size());
        int i = 0;
        for (int v : set) { if (i++ == idx) return v; }
        throw new IllegalStateException();
    }

    /**
     * Two precincts are considered adjacent if any pair of their polygon
     * vertices is within a small tolerance — a coarse but effective heuristic
     * that doesn't require true topology computation.
     */
    private static Map<String, List<String>> buildAdjacency(RedistrictingMap map) {
        List<Precinct> precincts = map.precincts();
        // Index vertices into a coarse spatial hash.
        double tol = 1e-6;
        Map<Long, List<Integer>> grid = new HashMap<>();
        List<double[][]> verts = new ArrayList<>(precincts.size());
        for (int i = 0; i < precincts.size(); i++) {
            Precinct p = precincts.get(i);
            List<double[]> ring = p.primaryRing();
            double[][] arr = ring.toArray(new double[0][]);
            verts.add(arr);
            for (double[] v : arr) {
                long key = cellKey(v[0], v[1], tol);
                grid.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            }
        }
        Map<String, Set<String>> adj = new HashMap<>();
        for (Precinct p : precincts) adj.put(p.id(), new HashSet<>());
        for (List<Integer> bucket : grid.values()) {
            for (int a : bucket) {
                for (int b : bucket) {
                    if (a < b) {
                        adj.get(precincts.get(a).id()).add(precincts.get(b).id());
                        adj.get(precincts.get(b).id()).add(precincts.get(a).id());
                    }
                }
            }
        }
        Map<String, List<String>> out = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : adj.entrySet()) {
            out.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return out;
    }

    private static long cellKey(double x, double y, double tol) {
        long ix = Math.round(x / tol);
        long iy = Math.round(y / tol);
        return (ix * 73856093L) ^ (iy * 19349663L);
    }

    /** Sum of perimeters of district outer rings (approximated as sum of precinct perimeters). */
    private static double districtPerimeter(District d) {
        // Approximation: sum precinct perimeters. Internal edges are double-counted,
        // which slightly under-rewards compactness — acceptable for relative scoring.
        double sum = 0;
        for (Precinct p : d.precincts()) sum += p.perimeter();
        return sum;
    }

    private static double efficiencyGap(List<District> districts) {
        long wastedD = 0, wastedR = 0;
        long total = 0;
        for (District d : districts) {
            int dem = d.totalDemVotes();
            int rep = d.totalRepVotes();
            int sum = dem + rep;
            if (sum == 0) continue;
            int needed = sum / 2 + 1;
            if (dem > rep) {
                wastedD += dem - needed;   // surplus winning votes
                wastedR += rep;            // all losing votes
            } else {
                wastedR += rep - needed;
                wastedD += dem;
            }
            total += sum;
        }
        if (total == 0) return 0;
        return (double) (wastedR - wastedD) / total;
    }
}
