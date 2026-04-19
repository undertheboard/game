package com.redistricting.io;

import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rescales precinct populations so each county's total matches a known
 * census figure, while preserving each precinct's relative share within
 * its county.
 *
 * <p>Real precinct shapefiles distributed by the Redistricting Data Hub
 * (and bundled with this app as {@code presets/nc_2024_pres.geojson.gz})
 * carry a {@code TOTPOP} column that is in fact <em>total ballots cast</em>
 * — typically {@code Dem + Rep + minor-party + write-in} — not census
 * residents. That is fine for vote-share math but it understates the actual
 * population by ~45%, and population deviation reported by the fairness
 * analyzer ends up roughly the right shape but the wrong magnitude.
 *
 * <p>This class fixes that by reading a small CSV resource (one row per
 * county: {@code COUNTY,POPULATION}) and, for each precinct, multiplying
 * its existing population by
 * {@code censusPop(county) / sumOfBallotsCast(county)}. Counties not
 * present in the table are passed through unchanged. The within-county
 * distribution is preserved exactly, so the fact that we don't have
 * precinct-level census disaggregation doesn't matter for redistricting at
 * the precinct level — what matters is that totals are right.
 */
public final class CountyPopulationScaler {

    private CountyPopulationScaler() {}

    /**
     * Load the bundled NC 2020 county census table and apply it to a
     * freshly-loaded NC precinct base.
     */
    public static RedistrictingMap rescaleNc2020(RedistrictingMap map) throws IOException {
        Map<String, Long> census = loadCsv("presets/nc_2020_county_pop.csv");
        return rescale(map, census);
    }

    /**
     * Apply {@code census} (county name → census total) to {@code map},
     * returning a new map whose precinct populations sum (per county) to
     * the census totals where known. Counties absent from {@code census}
     * are returned unchanged.
     */
    public static RedistrictingMap rescale(RedistrictingMap map,
                                           Map<String, Long> census) {
        // Sum current precinct population per county.
        Map<String, Long> currentByCounty = new HashMap<>();
        for (Precinct p : map.precincts()) {
            currentByCounty.merge(countyOf(p), (long) p.population(), Long::sum);
        }
        // Per-county multiplier (only for counties present in both maps).
        Map<String, Double> mult = new HashMap<>();
        for (Map.Entry<String, Long> e : census.entrySet()) {
            long current = currentByCounty.getOrDefault(e.getKey(), 0L);
            if (current <= 0) continue;
            mult.put(e.getKey(), e.getValue() / (double) current);
        }
        if (mult.isEmpty()) return map; // nothing to do — leave the map alone.

        List<Precinct> rescaled = new ArrayList<>(map.precincts().size());
        for (Precinct p : map.precincts()) {
            Double m = mult.get(countyOf(p));
            int newPop = (m == null)
                    ? p.population()
                    : (int) Math.max(0, Math.round(p.population() * m));
            rescaled.add(new Precinct(p.id(), p.district(), newPop,
                    p.demVotes(), p.repVotes(), p.rings()));
        }
        return new RedistrictingMap(map.name(), map.districtCount(), rescaled);
    }

    /**
     * Extract the county name from a precinct id of the form
     * {@code COUNTY-:-PRECINCT} (the convention used by RDH-style ids).
     * Falls back to the whole id if no separator is present.
     */
    private static String countyOf(Precinct p) {
        String id = p.id();
        int sep = id.indexOf("-:-");
        String county = (sep > 0) ? id.substring(0, sep) : id;
        return county.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, Long> loadCsv(String resourcePath) throws IOException {
        ClassLoader cl = CountyPopulationScaler.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IOException("resource not found: " + resourcePath);
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                Map<String, Long> out = new HashMap<>();
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int comma = line.indexOf(',');
                    if (comma <= 0) continue;
                    String name = line.substring(0, comma).trim().toUpperCase(Locale.ROOT);
                    String value = line.substring(comma + 1).trim();
                    try {
                        out.put(name, Long.parseLong(value));
                    } catch (NumberFormatException ignored) {
                        // skip non-numeric rows (header lines / commentary)
                    }
                }
                return out;
            }
        }
    }
}
