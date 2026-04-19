package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;

import java.util.Random;

/**
 * <strong>Partisan-target</strong> redistricting algorithm.
 *
 * <p><em>Goal:</em> hit the partisan-bias slider's requested seat count as
 * closely as possible, e.g. "draw 7 Democratic and 5 Republican seats out
 * of 12". Effectively a controlled gerrymander; useful for simulating
 * adversarial plans.
 *
 * <p><em>Method:</em> uses {@link AdvancedMultiObjectiveAlgorithm} under
 * the hood for growth and local search, then keeps re-running attempts and
 * picks the one whose seat count is closest to the target — heavily
 * weighting the seat-gap term in selection so the partisan goal dominates
 * tie-breaks.
 */
public final class PartisanTargetAlgorithm implements RedistrictingAlgorithm {

    private final AdvancedMultiObjectiveAlgorithm core =
            new AdvancedMultiObjectiveAlgorithm();

    @Override public String id() { return "partisan"; }
    @Override public String displayName() { return "Partisan target — seat count"; }
    @Override public String description() {
        return "Maximises the chance of hitting the partisan-bias slider's target "
             + "Dem-seat count. Uses the same multi-objective core but selects "
             + "attempts primarily on the seat-gap. Useful for adversarial / "
             + "gerrymander analysis.";
    }

    @Override
    public int[] assign(PrecinctBase base, GenerationParams params) {
        int target = AdvancedMultiObjectiveAlgorithm.targetDemSeats(
                params.partisanBias(), params.districts());
        Random rng = new Random(params.seed());
        int[] best = null;
        int bestSeatGap = Integer.MAX_VALUE;
        double bestSecondary = Double.POSITIVE_INFINITY;
        BoundaryRefiner.Objective obj = AdvancedMultiObjectiveAlgorithm.makeObjective(
                base, params, target);

        for (int attempt = 0; attempt < params.attempts(); attempt++) {
            long attemptSeed = rng.nextLong();
            int[] assignment = core.assign(base, params.withSeed(attemptSeed));
            BoundaryRefiner.Stats stats = BoundaryRefiner.statsOf(assignment,
                    params.districts(), base.precincts(), base.county(), base.counties());
            int demSeats = AdvancedMultiObjectiveAlgorithm.countDemSeats(stats,
                    params.districts());
            int gap = Math.abs(demSeats - target);
            double secondary = obj.score(stats, params.districts());
            if (gap < bestSeatGap
                    || (gap == bestSeatGap && secondary < bestSecondary)) {
                bestSeatGap = gap;
                bestSecondary = secondary;
                best = assignment;
            }
            if (gap == 0 && attempt >= 2) break; // already on-target — stop early
        }
        return best;
    }
}
