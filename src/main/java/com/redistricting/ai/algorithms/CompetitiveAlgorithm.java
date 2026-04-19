package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;

import java.util.Random;

/**
 * <strong>Competitive</strong> redistricting algorithm.
 *
 * <p><em>Goal:</em> draw districts that are as close to 50/50 partisan as
 * possible, maximising the number of competitive seats.
 *
 * <p><em>Method:</em> the {@link CompactnessAlgorithm} produces a clean
 * spatial partition; we then re-run the boundary local search with an
 * objective that penalises {@code |Dem share − 50%|} in every district while
 * keeping populations balanced.
 */
public final class CompetitiveAlgorithm implements RedistrictingAlgorithm {

    private final CompactnessAlgorithm spatial = new CompactnessAlgorithm();

    @Override public String id() { return "competitive"; }
    @Override public String displayName() { return "Competitive — many swing seats"; }
    @Override public String description() {
        return "Spatially partitions the state with k-means, then refines boundaries to "
             + "minimise |Dem share − 50%| in every district while keeping populations "
             + "balanced. Maximises competitive (swing) seats.";
    }

    @Override
    public int[] assign(PrecinctBase base, GenerationParams params) {
        Random masterRng = new Random(params.seed());

        int[] best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        BoundaryRefiner.Objective obj = competitiveObjective(base, params);

        for (int attempt = 0; attempt < params.attempts(); attempt++) {
            long attemptSeed = masterRng.nextLong();
            // Build a baseline plan with the compactness algorithm — by passing
            // a sub-params with attempts=1 we keep wall-time low.
            int[] assignment = spatial.assign(base, params.withSeed(attemptSeed));

            BoundaryRefiner.refine(assignment, params.districts(), base.precincts(),
                    base.county(), base.counties(), base.adjacency(), obj,
                    /*passes*/ 12, new Random(attemptSeed ^ 0xC0FFEE));
            GeographyUtils.repairContiguity(assignment, base.adjacency(),
                    params.districts(), 4);

            BoundaryRefiner.Stats stats = BoundaryRefiner.statsOf(assignment,
                    params.districts(), base.precincts(), base.county(), base.counties());
            double score = obj.score(stats, params.districts());
            if (score < bestScore) { bestScore = score; best = assignment; }
        }
        return best;
    }

    private BoundaryRefiner.Objective competitiveObjective(PrecinctBase base,
                                                            GenerationParams p) {
        long total = base.totalPopulation();
        final double ideal = (double) total / p.districts();
        return (s, D) -> {
            double popDev = 0;
            double leanSum = 0;
            for (int d = 0; d < D; d++) {
                popDev = Math.max(popDev, Math.abs(s.pop[d] - ideal) / ideal);
                long votes = s.dem[d] + s.rep[d];
                if (votes > 0) {
                    double share = (double) s.dem[d] / votes;
                    leanSum += Math.abs(share - 0.5);
                }
            }
            double leanAvg = leanSum / D;
            return 3.0 * popDev + 4.0 * leanAvg;
        };
    }
}
