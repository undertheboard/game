package com.redistricting.ai;

import com.redistricting.ai.algorithms.Algorithms;

/**
 * Knobs for {@link MapGenerator#generate}. All values are validated in the
 * compact constructor.
 *
 * @param districts          number of districts to draw (>= 2)
 * @param precinctsX         synthetic precinct grid width  (>= 4) — only
 *                           used when generating against a synthetic base;
 *                           ignored when running against a real precinct
 *                           base loaded from the Redistricting Data Hub
 * @param precinctsY         synthetic precinct grid height (>= 4) — see above
 * @param countiesX          synthetic county grid width
 * @param countiesY          synthetic county grid height
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
 * @param algorithm          id of the {@link com.redistricting.ai.algorithms.RedistrictingAlgorithm}
 *                           to dispatch to (see {@link Algorithms#byId})
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
        long seed,
        String algorithm,
        SeatTargets seatTargets
) {
    /**
     * Optional explicit seat-count target. When non-null this overrides the
     * {@link #partisanBias} slider for any algorithm that consumes
     * {@link com.redistricting.ai.algorithms.AdvancedMultiObjectiveAlgorithm#targetDemSeats}.
     *
     * <p>{@code dem + rep + tossup} need not equal the total district count
     * — surplus seats are allocated to whichever bucket is closest to the
     * total in proportion. {@link #demTarget()} is the canonical Dem-seat
     * goal the generator optimises against.
     */
    public record SeatTargets(int dem, int rep, int tossup) {
        public SeatTargets {
            if (dem < 0)    dem = 0;
            if (rep < 0)    rep = 0;
            if (tossup < 0) tossup = 0;
        }
        /** Total seats requested across all three buckets. */
        public int total() { return dem + rep + tossup; }
        /**
         * Convert the (dem, rep, tossup) triple into the single Dem-seat
         * target the optimiser can compare against, by treating tossup
         * seats as half a Dem seat each (i.e. neutral expectation).
         */
        public int demTarget(int districtCount) {
            if (total() == 0) return Math.max(0, districtCount / 2);
            double scale = (double) districtCount / total();
            double demSeats = dem * scale + 0.5 * tossup * scale;
            return Math.max(0, Math.min(districtCount, (int) Math.round(demSeats)));
        }
    }

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
        if (algorithm == null || algorithm.isBlank()) algorithm = Algorithms.SIMPLE.id();
    }

    /** Backwards-compatible constructor with no explicit seat targets. */
    public GenerationParams(int districts, int precinctsX, int precinctsY,
                            int countiesX, int countiesY, int partisanBias,
                            double countyAdherence, double compactness,
                            double populationTolerance, double reliability,
                            long seed, String algorithm) {
        this(districts, precinctsX, precinctsY, countiesX, countiesY, partisanBias,
                countyAdherence, compactness, populationTolerance, reliability, seed,
                algorithm, null);
    }

    /** Backwards-compatible constructor that defaults the algorithm to "simple". */
    public GenerationParams(int districts, int precinctsX, int precinctsY,
                            int countiesX, int countiesY, int partisanBias,
                            double countyAdherence, double compactness,
                            double populationTolerance, double reliability,
                            long seed) {
        this(districts, precinctsX, precinctsY, countiesX, countiesY, partisanBias,
                countyAdherence, compactness, populationTolerance, reliability, seed,
                Algorithms.SIMPLE.id(), null);
    }

    /** Number of independent generation attempts, derived from reliability. */
    public int attempts() {
        return Math.max(1, (int) Math.round(1 + reliability * 19));
    }

    /** Return a copy with a different PRNG seed. */
    public GenerationParams withSeed(long newSeed) {
        return new GenerationParams(districts, precinctsX, precinctsY,
                countiesX, countiesY, partisanBias, countyAdherence, compactness,
                populationTolerance, reliability, newSeed, algorithm, seatTargets);
    }

    /** Return a copy with a different algorithm id. */
    public GenerationParams withAlgorithm(String newAlgorithm) {
        return new GenerationParams(districts, precinctsX, precinctsY,
                countiesX, countiesY, partisanBias, countyAdherence, compactness,
                populationTolerance, reliability, seed, newAlgorithm, seatTargets);
    }

    /**
     * Effective Dem-seat target: explicit {@link SeatTargets#demTarget} when
     * provided, otherwise derived from the partisan-bias slider. Exposed as
     * a single helper so every algorithm goes through the same code path.
     */
    public int effectiveDemTarget() {
        if (seatTargets != null && seatTargets.total() > 0) {
            return seatTargets.demTarget(districts);
        }
        // Mirror AdvancedMultiObjectiveAlgorithm.targetDemSeats(bias, districts).
        double frac = 0.5 + (partisanBias / 100.0) * 0.5;
        int seats = (int) Math.round(frac * districts);
        return Math.max(0, Math.min(districts, seats));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
