package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.util.List;
import java.util.Random;

/**
 * <strong>Competitive</strong> redistricting algorithm.
 *
 * <p><em>Goal:</em> draw districts that are as close to 50/50 partisan as
 * possible, maximising the number of competitive seats. Useful for studying
 * what a low-gerrymandering plan looks like, or for generating "swing
 * district" baselines.
 *
 * <p><em>Method:</em> we build the same Lloyd-style spatial partition as
 * {@link CompactnessAlgorithm} so districts stay coherent, then drive a
 * boundary local-search whose objective penalises partisan share deviation
 * from 50% rather than from a per-district target. Population balance is
 * enforced with the same hard guard.
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
    public RedistrictingMap generate(GenerationParams params) {
        // Use the compactness algorithm to produce a good geographic baseline,
        // then re-refine with a competitiveness objective.
        Random masterRng = new Random(params.seed());
        int[] bestAssignment = null;
        List<Precinct> bestPrecincts = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int attempt = 0; attempt < params.attempts(); attempt++) {
            long attemptSeed = masterRng.nextLong();
            // Build a baseline plan with the compactness algorithm, then
            // re-optimise the assignment with a competitiveness objective.
            GenerationParams subParams = withSeed(params, attemptSeed);
            RedistrictingMap base = spatial.generate(subParams);

            int[] assignment = new int[base.precincts().size()];
            for (int i = 0; i < assignment.length; i++) {
                assignment[i] = base.precincts().get(i).district();
            }
            int[] county = GeographyUtils.countyOf(params);
            int counties = params.countiesX() * params.countiesY();
            int[][] adj = GeographyUtils.gridAdjacency(params.precinctsX(), params.precinctsY());

            BoundaryRefiner.Objective obj = competitiveObjective(base.precincts(), params);
            BoundaryRefiner.refine(assignment, params.districts(), base.precincts(), county,
                    counties, adj, obj, /*passes*/ 12, new Random(attemptSeed ^ 0xC0FFEE));
            GeographyUtils.repairContiguity(assignment, adj, params.districts(), 4);

            BoundaryRefiner.Stats finalStats = BoundaryRefiner.statsOf(
                    assignment, params.districts(), base.precincts(), county, counties);
            double score = obj.score(finalStats, params.districts());
            if (score < bestScore) {
                bestScore = score;
                bestAssignment = assignment;
                bestPrecincts = base.precincts();
            }
        }
        return AdvancedMultiObjectiveAlgorithm.materialise(
                bestPrecincts, bestAssignment, params,
                "Generated Plan (Competitive — swing seats)");
    }

    private GenerationParams withSeed(GenerationParams p, long seed) {
        return new GenerationParams(p.districts(), p.precinctsX(), p.precinctsY(),
                p.countiesX(), p.countiesY(), p.partisanBias(),
                p.countyAdherence(), p.compactness(), p.populationTolerance(),
                p.reliability(), seed, p.algorithm());
    }

    private BoundaryRefiner.Objective competitiveObjective(List<Precinct> precincts,
                                                            GenerationParams p) {
        long total = 0;
        for (Precinct pr : precincts) total += pr.population();
        final double ideal = (double) total / p.districts();
        return (s, D) -> {
            double popDev = 0;
            double leanSum = 0;
            for (int d = 0; d < D; d++) {
                popDev = Math.max(popDev, Math.abs(s.pop[d] - ideal) / ideal);
                int votes = s.dem[d] + s.rep[d];
                if (votes > 0) {
                    double share = (double) s.dem[d] / votes;
                    leanSum += Math.abs(share - 0.5);
                }
            }
            double leanAvg = leanSum / D;
            // Population deviation dominates as a hard constraint; lean drives
            // the optimisation toward 50/50 districts.
            return 3.0 * popDev + 4.0 * leanAvg;
        };
    }
}
