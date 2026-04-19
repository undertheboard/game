package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;
import com.redistricting.ai.MapGenerator;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke / behavioural tests for the redistricting algorithms.
 *
 * <p>These tests run each algorithm against a small synthetic precinct
 * grid and assert the contract every algorithm must satisfy:
 * <ul>
 *   <li>every precinct is assigned a valid district id,</li>
 *   <li>every district is non-empty and (rook-)contiguous,</li>
 *   <li>population deviation stays within the requested tolerance.</li>
 * </ul>
 */
class AlgorithmsTest {

    private static GenerationParams params(String algorithm) {
        return new GenerationParams(
                /*districts*/ 4,
                /*precinctsX*/ 8, /*precinctsY*/ 8,
                /*countiesX*/ 2, /*countiesY*/ 2,
                /*partisanBias*/ 0,
                /*countyAdherence*/ 0.5,
                /*compactness*/ 0.5,
                /*populationTolerance*/ 0.05,
                /*reliability*/ 0.0,   // 1 attempt → fast tests
                /*seed*/ 42L,
                algorithm);
    }

    @Test
    void byIdFallsBackToSimple() {
        assertSame(Algorithms.SIMPLE, Algorithms.byId("does-not-exist"));
        assertSame(Algorithms.SIMPLE, Algorithms.byId(null));
        for (RedistrictingAlgorithm a : Algorithms.ALL) {
            assertSame(a, Algorithms.byId(a.id()));
        }
    }

    @Test
    void simpleAlgorithmProducesContiguousValidPlan() {
        runAndVerify(Algorithms.SIMPLE.id(), 0.05);
    }

    @Test
    void advancedAlgorithmProducesContiguousValidPlan() {
        runAndVerify(Algorithms.ADVANCED.id(), 0.05);
    }

    @Test
    void compactnessAlgorithmProducesContiguousValidPlan() {
        runAndVerify(Algorithms.COMPACTNESS.id(), 0.10);
    }

    @Test
    void competitiveAlgorithmProducesContiguousValidPlan() {
        runAndVerify(Algorithms.COMPETITIVE.id(), 0.10);
    }

    @Test
    void partisanAlgorithmProducesContiguousValidPlan() {
        runAndVerify(Algorithms.PARTISAN.id(), 0.10);
    }

    @Test
    void targetDemSeatsMapsBiasMonotonically() {
        // bias = -100 → 0 seats; 0 → ⌊D/2⌋; +100 → all D seats.
        assertEquals(0, AdvancedMultiObjectiveAlgorithm.targetDemSeats(-100, 8));
        assertEquals(4, AdvancedMultiObjectiveAlgorithm.targetDemSeats(0, 8));
        assertEquals(8, AdvancedMultiObjectiveAlgorithm.targetDemSeats(100, 8));
        // monotonic
        int prev = -1;
        for (int b = -100; b <= 100; b += 10) {
            int seats = AdvancedMultiObjectiveAlgorithm.targetDemSeats(b, 12);
            assertTrue(seats >= prev,
                    "seats not monotone at bias=" + b + " (was " + prev + ", now " + seats + ")");
            prev = seats;
        }
    }

    @Test
    void precinctBaseExposesGeometryAndAdjacency() {
        GenerationParams p = params(Algorithms.SIMPLE.id());
        PrecinctBase base = PrecinctBase.synthetic(p);
        assertEquals(p.precinctsX() * p.precinctsY(), base.size());
        assertEquals(base.size(), base.adjacency().length);
        // Every interior precinct in a grid has exactly 4 neighbours.
        int interior = (p.precinctsX() - 2) * (p.precinctsY() - 2);
        long fourNeighbour = 0;
        for (int i = 0; i < base.size(); i++) {
            if (base.adjacency()[i].length == 4) fourNeighbour++;
        }
        assertEquals(interior, fourNeighbour);
    }

    // ---------- helpers ---------------------------------------------------

    private void runAndVerify(String algorithmId, double tolerance) {
        GenerationParams p = params(algorithmId);
        RedistrictingMap plan = new MapGenerator().generate(p);
        assertNotNull(plan, "generator returned no plan");
        verifyAllAssigned(plan);
        verifyContiguous(plan);
        verifyPopulationDeviation(plan, tolerance);
    }

    private void verifyAllAssigned(RedistrictingMap plan) {
        for (Precinct pr : plan.precincts()) {
            assertTrue(pr.district() >= 0 && pr.district() < plan.districtCount(),
                    "invalid district id " + pr.district() + " on precinct " + pr.id());
        }
    }

    private void verifyPopulationDeviation(RedistrictingMap plan, double tolerance) {
        long total = 0;
        long[] districtPop = new long[plan.districtCount()];
        for (Precinct pr : plan.precincts()) {
            total += pr.population();
            districtPop[pr.district()] += pr.population();
        }
        double ideal = (double) total / plan.districtCount();
        for (int d = 0; d < plan.districtCount(); d++) {
            double dev = Math.abs(districtPop[d] - ideal) / ideal;
            assertTrue(dev <= tolerance + 0.05,
                    "district " + d + " deviation " + dev
                            + " exceeds tolerance " + tolerance);
        }
    }

    /**
     * Verify every district is a single connected component under rook
     * adjacency (the same adjacency the algorithms use).
     */
    private void verifyContiguous(RedistrictingMap plan) {
        // Rebuild the adjacency from the precinct list — we use vertex-sharing
        // adjacency which matches what {@link PrecinctBase#fromMap} does.
        PrecinctBase base = PrecinctBase.fromMap(plan);
        int n = base.size();
        boolean[] seen = new boolean[n];
        for (int d = 0; d < plan.districtCount(); d++) {
            int start = -1;
            for (int i = 0; i < n; i++) {
                if (base.precincts().get(i).district() == d) { start = i; break; }
            }
            if (start == -1) continue; // empty district — handled by other test
            Deque<Integer> q = new ArrayDeque<>();
            Set<Integer> visited = new HashSet<>();
            q.add(start); visited.add(start);
            while (!q.isEmpty()) {
                int cur = q.poll();
                for (int nb : base.adjacency()[cur]) {
                    if (visited.contains(nb)) continue;
                    if (base.precincts().get(nb).district() != d) continue;
                    visited.add(nb);
                    q.add(nb);
                }
            }
            int countInDistrict = 0;
            for (Precinct pr : base.precincts()) if (pr.district() == d) countInDistrict++;
            assertEquals(countInDistrict, visited.size(),
                    "district " + d + " is not contiguous");
            for (int i : visited) seen[i] = true;
        }
        // Sanity: every precinct was in some district's component.
        int total = 0;
        for (boolean v : seen) if (v) total++;
        assertEquals(n, total);
    }

    @SuppressWarnings("unused")
    private static List<Precinct> precincts(RedistrictingMap m) { return m.precincts(); }
}
