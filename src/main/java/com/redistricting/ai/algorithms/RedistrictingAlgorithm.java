package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;

/**
 * Strategy interface for assigning precincts to districts.
 *
 * <p>Implementations are responsible for:
 * <ol>
 *   <li>Accepting a {@link PrecinctBase} (real precincts loaded from the
 *       Redistricting Data Hub, or a synthetic grid for demos/tests).</li>
 *   <li>Assigning every precinct to exactly one of the requested districts.</li>
 *   <li>Returning an assignment whose districts are <em>contiguous</em>
 *       under {@link PrecinctBase#adjacency()} and within the configured
 *       population tolerance whenever feasible.</li>
 * </ol>
 *
 * <p>Algorithms differ in their <strong>goals</strong>: some optimise for
 * geometric compactness, some for partisan competitiveness, some for raw
 * speed, some for hitting a target seat count. See the implementations in
 * this package for the goal of each algorithm.
 */
public interface RedistrictingAlgorithm {

    /** Stable, machine-readable id (used by the params record). */
    String id();

    /** Short human-readable name for the GUI. */
    String displayName();

    /** A one-paragraph description of the algorithm's goal and behaviour. */
    String description();

    /** Whether this algorithm is exposed in the "Simple" generator UI. */
    default boolean isSimple() { return false; }

    /**
     * Run the algorithm.
     *
     * @param base   the precinct substrate (real RDH precincts or synthetic).
     * @param params validated parameters; algorithms must respect at least
     *               {@code districts} and {@code seed}. The synthetic-grid
     *               sizing fields are ignored when {@code base} was loaded
     *               from real data.
     * @return per-precinct district assignment ({@code [0, districts)}),
     *         parallel to {@code base.precincts()}.
     */
    int[] assign(PrecinctBase base, GenerationParams params);
}
