package com.redistricting.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole redistricting plan: a named state, a set of precincts, and the
 * number of districts the precincts are partitioned into.
 */
public final class RedistrictingMap {

    private final String name;
    private final int districtCount;
    private final List<Precinct> precincts;

    public RedistrictingMap(String name, int districtCount, List<Precinct> precincts) {
        if (districtCount <= 0) {
            throw new IllegalArgumentException("districtCount must be > 0");
        }
        if (precincts == null || precincts.isEmpty()) {
            throw new IllegalArgumentException("at least one precinct is required");
        }
        for (Precinct p : precincts) {
            if (p.district() < 0 || p.district() >= districtCount) {
                throw new IllegalArgumentException(
                        "precinct " + p.id() + " assigned to invalid district " + p.district());
            }
        }
        this.name = name == null ? "Unnamed" : name;
        this.districtCount = districtCount;
        this.precincts = new ArrayList<>(precincts);
    }

    public String name() { return name; }
    public int districtCount() { return districtCount; }
    public List<Precinct> precincts() { return precincts; }

    public int totalPopulation() {
        int sum = 0;
        for (Precinct p : precincts) sum += p.population();
        return sum;
    }

    /** Returns the {@link District} objects derived from current precinct assignments. */
    public List<District> districts() {
        Map<Integer, List<Precinct>> byId = new HashMap<>();
        for (int i = 0; i < districtCount; i++) byId.put(i, new ArrayList<>());
        for (Precinct p : precincts) byId.get(p.district()).add(p);
        List<District> out = new ArrayList<>(districtCount);
        for (int i = 0; i < districtCount; i++) out.add(new District(i, byId.get(i)));
        return out;
    }
}
