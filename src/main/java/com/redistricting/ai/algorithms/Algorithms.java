package com.redistricting.ai.algorithms;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue of all available {@link RedistrictingAlgorithm} implementations.
 *
 * <p>The catalogue exposes a stable id → algorithm lookup for the dispatcher
 * in {@code MapGenerator} and an ordered list for the GUI dropdown. Adding a
 * new algorithm to the project means adding one line to {@link #ALL}.
 */
public final class Algorithms {

    public static final SimpleAlgorithm SIMPLE = new SimpleAlgorithm();
    public static final AdvancedMultiObjectiveAlgorithm ADVANCED =
            new AdvancedMultiObjectiveAlgorithm();
    public static final CompactnessAlgorithm COMPACTNESS = new CompactnessAlgorithm();
    public static final CompetitiveAlgorithm COMPETITIVE = new CompetitiveAlgorithm();
    public static final PartisanTargetAlgorithm PARTISAN = new PartisanTargetAlgorithm();

    /** Display order — Simple first so it appears at the top of pickers. */
    public static final List<RedistrictingAlgorithm> ALL = List.of(
            SIMPLE, ADVANCED, COMPACTNESS, COMPETITIVE, PARTISAN);

    private static final Map<String, RedistrictingAlgorithm> BY_ID;
    static {
        BY_ID = new LinkedHashMap<>();
        for (RedistrictingAlgorithm a : ALL) BY_ID.put(a.id(), a);
    }

    private Algorithms() {}

    /** Look up an algorithm by id, falling back to {@link #SIMPLE} when unknown. */
    public static RedistrictingAlgorithm byId(String id) {
        if (id == null) return SIMPLE;
        RedistrictingAlgorithm a = BY_ID.get(id);
        return a == null ? SIMPLE : a;
    }

    /** The algorithms appropriate for the "Simple" UI mode. */
    public static List<RedistrictingAlgorithm> simpleOnes() {
        return ALL.stream().filter(RedistrictingAlgorithm::isSimple).toList();
    }
}
