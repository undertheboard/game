package com.redistricting.ai;

import com.redistricting.io.DraGeoJsonLoader;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FairnessAnalyzerTest {

    @Test
    void perfectlyEqualPopulationsProduceZeroDeviation() {
        RedistrictingMap map = squareMap(2, 2, 1000, 1000); // 2 districts, equal pop
        FairnessReport r = new FairnessAnalyzer().analyze(map);
        assertEquals(0.0, r.populationDeviation(), 1e-9);
    }

    @Test
    void unequalPopulationsRaiseDeviation() {
        RedistrictingMap map = squareMap(2, 2, 1500, 500); // 75% / 25%
        FairnessReport r = new FairnessAnalyzer().analyze(map);
        assertTrue(r.populationDeviation() > 0.4,
                "expected sizable deviation, got " + r.populationDeviation());
    }

    @Test
    void efficiencyGapZeroWhenSymmetricPlan() {
        // District A: 60 Dem / 40 Rep; District B: 40 Dem / 60 Rep — symmetric.
        RedistrictingMap map = squareMapVotes(2, 1000, 600, 400, 400, 600);
        FairnessReport r = new FairnessAnalyzer().analyze(map);
        assertEquals(0.0, r.efficiencyGap(), 1e-9);
    }

    @Test
    void efficiencyGapNonZeroForCrackedPlan() {
        // District A wins 80-20 (huge surplus), District B loses 45-55 narrowly.
        RedistrictingMap map = squareMapVotes(2, 1000, 800, 200, 450, 550);
        FairnessReport r = new FairnessAnalyzer().analyze(map);
        assertTrue(Math.abs(r.efficiencyGap()) > 0.05,
                "expected significant efficiency gap, got " + r.efficiencyGap());
    }

    @Test
    void analyzerLoadsBundledSample() throws Exception {
        RedistrictingMap map = DraGeoJsonLoader.loadFromResource("sample-dra.geojson");
        assertNotNull(map);
        assertEquals(6, map.districtCount());
        FairnessReport r = new FairnessAnalyzer().analyze(map);
        assertNotNull(r.prettyPrint(map.name()));
        assertTrue(r.unfairnessScore() >= 0);
    }

    // --- helpers -----------------------------------------------------------

    /** Build N square precincts laid out in a row, alternating district. */
    private static RedistrictingMap squareMap(int districts, int precinctsPerDistrict,
                                              int popA, int popB) {
        List<Precinct> ps = new ArrayList<>();
        for (int i = 0; i < districts * precinctsPerDistrict; i++) {
            int d = i % districts;
            int pop = d == 0 ? popA / precinctsPerDistrict : popB / precinctsPerDistrict;
            ps.add(squarePrecinct("P" + i, d, pop, 0, 0, i, 0));
        }
        return new RedistrictingMap("test", districts, ps);
    }

    private static RedistrictingMap squareMapVotes(int districts, int popPerDistrict,
                                                   int demA, int repA, int demB, int repB) {
        List<Precinct> ps = new ArrayList<>();
        ps.add(squarePrecinct("P0", 0, popPerDistrict, demA, repA, 0, 0));
        ps.add(squarePrecinct("P1", 1, popPerDistrict, demB, repB, 1, 0));
        return new RedistrictingMap("test", districts, ps);
    }

    private static Precinct squarePrecinct(String id, int district, int pop,
                                           int dem, int rep, int x, int y) {
        List<double[]> ring = List.of(
                new double[] { x, y },
                new double[] { x + 1, y },
                new double[] { x + 1, y + 1 },
                new double[] { x, y + 1 });
        return new Precinct(id, district, pop, dem, rep, List.of(ring));
    }
}
