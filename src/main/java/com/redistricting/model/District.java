package com.redistricting.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A district is a collection of precincts that together elect one representative.
 * District objects are derived/computed from a {@link RedistrictingMap}; the
 * authoritative assignment lives on each {@link Precinct}.
 */
public final class District {

    private final int id;
    private final List<Precinct> precincts;

    public District(int id, List<Precinct> precincts) {
        this.id = id;
        this.precincts = Collections.unmodifiableList(new ArrayList<>(precincts));
    }

    public int id() { return id; }
    public List<Precinct> precincts() { return precincts; }

    public int totalPopulation() {
        int sum = 0;
        for (Precinct p : precincts) sum += p.population();
        return sum;
    }

    public int totalDemVotes() {
        int sum = 0;
        for (Precinct p : precincts) sum += p.demVotes();
        return sum;
    }

    public int totalRepVotes() {
        int sum = 0;
        for (Precinct p : precincts) sum += p.repVotes();
        return sum;
    }

    public double totalArea() {
        double sum = 0;
        for (Precinct p : precincts) sum += p.area();
        return sum;
    }

    /** Winner: 0 = Dem, 1 = Rep, -1 = tie / no votes. */
    public int winner() {
        int d = totalDemVotes();
        int r = totalRepVotes();
        if (d == r) return -1;
        return d > r ? 0 : 1;
    }
}
