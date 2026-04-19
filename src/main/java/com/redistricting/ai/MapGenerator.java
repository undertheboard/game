package com.redistricting.ai;

import com.redistricting.ai.algorithms.Algorithms;
import com.redistricting.ai.algorithms.PrecinctBase;
import com.redistricting.ai.algorithms.RedistrictingAlgorithm;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatcher: looks up the {@link RedistrictingAlgorithm} named by
 * {@link GenerationParams#algorithm()} and runs it against a
 * {@link PrecinctBase}.
 *
 * <p>The legacy single-arg overload is retained so existing callers and
 * tests that don't yet hold a {@link PrecinctBase} keep working: they get
 * a synthetic grid base built from the params.
 */
public final class MapGenerator {

    /**
     * Run the configured algorithm against {@code base} and return the
     * resulting plan. The base's geographic data is reused; only the
     * district assignment is the algorithm's contribution.
     */
    public RedistrictingMap generate(PrecinctBase base, GenerationParams params) {
        RedistrictingAlgorithm alg = Algorithms.byId(params.algorithm());
        int[] assignment = alg.assign(base, params);
        return materialise(base, assignment, params, alg);
    }

    /**
     * Convenience: build a synthetic precinct grid from {@code params} and
     * generate against it. Intended for demos / tests / the legacy GUI flow.
     */
    public RedistrictingMap generate(GenerationParams params) {
        return generate(PrecinctBase.synthetic(params), params);
    }

    private RedistrictingMap materialise(PrecinctBase base, int[] assignment,
                                         GenerationParams params,
                                         RedistrictingAlgorithm alg) {
        List<Precinct> originals = base.precincts();
        List<Precinct> assigned = new ArrayList<>(originals.size());
        for (int i = 0; i < originals.size(); i++) {
            Precinct p = originals.get(i);
            assigned.add(new Precinct(p.id(), assignment[i], p.population(),
                    p.demVotes(), p.repVotes(), p.rings()));
        }
        String name = String.format("Generated Plan — %s (bias %s, base %s)",
                alg.displayName(),
                signedBias(params.partisanBias()),
                base.label());
        return new RedistrictingMap(name, params.districts(), assigned);
    }

    private static String signedBias(int v) {
        return (v > 0 ? "D+" : v < 0 ? "R+" : "±") + Math.abs(v);
    }
}
