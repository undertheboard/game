package com.redistricting.ai;

/**
 * Knobs for {@link MapGenerator#generate}. All values are validated in the
 * compact constructor.
 *
 * @param districts          number of districts to draw (>= 2)
 * @param precinctsX         precinct grid width  (>= 4)
 * @param precinctsY         precinct grid height (>= 4)
 * @param countiesX          county grid width  (>= 1, <= precinctsX)
 * @param countiesY          county grid height (>= 1, <= precinctsY)
 * @param partisanBias       -100 (R+100, max Republican-favoring gerrymander)
 *                           through 0 (proportional) to +100 (D+100, max
 *                           Democratic-favoring gerrymander)
 * @param countyAdherence    0.0–1.0; how strongly the algorithm avoids
 *                           splitting counties between districts
 * @param compactness        0.0–1.0; weight on geometric compactness in the
 *                           growth heuristic
 * @param populationTolerance 0.0–0.10; allowed |deviation| from ideal population
 * @param reliability        0.0–1.0; controls how many independent attempts
 *                           the generator runs and keeps the best of
 * @param seed               PRNG seed for reproducibility
 */
public record GenerationParams(
        int districts,
        int precinctsX,
        int precinctsY,
        int countiesX,
        int countiesY,
        int partisanBias,
        double countyAdherence,
        double compactness,
        double populationTolerance,
        double reliability,
        long seed
) {
    public GenerationParams {
        if (districts < 2) throw new IllegalArgumentException("districts must be >= 2");
        if (precinctsX < 4 || precinctsY < 4) {
            throw new IllegalArgumentException("precinct grid must be at least 4x4");
        }
        if (countiesX < 1 || countiesY < 1
                || countiesX > precinctsX || countiesY > precinctsY) {
            throw new IllegalArgumentException("invalid county grid");
        }
        if (partisanBias < -100 || partisanBias > 100) {
            throw new IllegalArgumentException("partisanBias must be in [-100, 100]");
        }
        countyAdherence = clamp(countyAdherence, 0, 1);
        compactness = clamp(compactness, 0, 1);
        populationTolerance = clamp(populationTolerance, 0, 0.10);
        reliability = clamp(reliability, 0, 1);
    }

    /** Number of independent generation attempts, derived from reliability. */
    public int attempts() {
        return Math.max(1, (int) Math.round(1 + reliability * 19));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
