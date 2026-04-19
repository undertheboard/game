package com.redistricting.io;

import com.redistricting.ai.GenerationParams;
import com.redistricting.ai.MapGenerator;
import com.redistricting.ai.algorithms.Algorithms;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests for {@link PlanGeoJsonWriter} and {@link RdhPrecinctLoader}:
 * a generated plan should serialise to GeoJSON, parse cleanly via the DRA
 * loader, and (after applying a synthetic BEF) come back with the same
 * district assignment.
 */
class PlanRoundTripTest {

    @Test
    void planRoundTripsThroughGeoJson(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws Exception {
        GenerationParams p = new GenerationParams(4, 6, 6, 2, 2, 0,
                0.5, 0.5, 0.05, 0.0, 7L, Algorithms.SIMPLE.id());
        RedistrictingMap original = new MapGenerator().generate(p);

        Path file = tmp.resolve("plan.geojson");
        PlanGeoJsonWriter.write(original, file);
        assertTrue(Files.size(file) > 0);

        RedistrictingMap reloaded = DraGeoJsonLoader.parse(
                Files.readString(file), "plan");
        assertEquals(original.precincts().size(), reloaded.precincts().size());
        assertEquals(original.districtCount(), reloaded.districtCount());

        for (int i = 0; i < original.precincts().size(); i++) {
            Precinct a = original.precincts().get(i);
            Precinct b = reloaded.precincts().get(i);
            assertEquals(a.id(), b.id());
            assertEquals(a.district(), b.district(), "district mismatch on " + a.id());
            assertEquals(a.population(), b.population());
        }
    }

    @Test
    void befReassignsPrecinctsByGeoid() {
        RedistrictingMap base = new MapGenerator().generate(new GenerationParams(
                4, 4, 4, 2, 2, 0, 0.5, 0.5, 0.05, 0.0, 1L, Algorithms.SIMPLE.id()));
        StringBuilder csv = new StringBuilder("id,district\n");
        for (Precinct p : base.precincts()) {
            // Push everything into district 1 (1-based as in BEFs).
            csv.append(p.id()).append(",1\n");
        }
        RedistrictingMap reassigned = RdhPrecinctLoader.applyBef(base,
                csv.toString(), "test-bef");
        for (Precinct p : reassigned.precincts()) {
            assertEquals(0, p.district(), "BEF should map all precincts to district 0");
        }
    }
}
