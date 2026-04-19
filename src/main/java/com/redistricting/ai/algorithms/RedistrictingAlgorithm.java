package com.redistricting.ai.algorithms;

import com.redistricting.ai.GenerationParams;
import com.redistricting.model.RedistrictingMap;

/**
 * Strategy interface for generating a complete redistricting plan.
 *
 * <p>Implementations are responsible for:
 * <ol>
 *   <li>Building (or accepting) a precinct grid sized by {@link GenerationParams}.</li>
 *   <li>Assigning every precinct to exactly one of the requested districts.</li>
 *   <li>Producing a {@link RedistrictingMap} whose districts are
 *       <em>contiguous</em> (every district is a single connected component
 *       under rook adjacency) and within the configured population
 *       tolerance whenever feasible.</li>
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
     * @param params validated parameters; algorithms must respect at least
     *               {@code districts}, the precinct grid, and {@code seed}.
     * @return a fully assigned, contiguous redistricting plan.
     */
    RedistrictingMap generate(GenerationParams params);
}
