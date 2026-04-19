package com.redistricting.io;

import com.redistricting.model.RedistrictingMap;

import java.io.IOException;
import java.util.List;

/**
 * Registry of bundled state-level precinct presets.
 *
 * <p>Each preset ships as a (gzipped) precinct GeoJSON resource baked into
 * the jar. Presets give first-time users a one-click way to load a real
 * statewide base map without first downloading anything from the
 * <a href="https://redistrictingdatahub.org/">Redistricting Data Hub</a>.
 *
 * <p>Currently bundled:
 * <ul>
 *   <li><strong>North Carolina — 2024 General (Presidential)</strong>:
 *       2,658 precincts, sourced from the NC State Board of Elections via
 *       RDH. Each precinct carries the 2024 Harris (DEM) and Trump (REP)
 *       presidential vote totals. The base loads with no district assignment
 *       so the user can generate or import a plan.</li>
 * </ul>
 */
public final class Presets {

    private Presets() {}

    /** A bundled precinct base map preset. */
    public record Preset(String displayName, String resourcePath) {
        /** Load the preset into a fresh {@link RedistrictingMap}. */
        public RedistrictingMap load() throws IOException {
            return RdhPrecinctLoader.loadPresetGeoJson(resourcePath, displayName);
        }
    }

    public static final Preset NC_2024_PRESIDENTIAL = new Preset(
            "North Carolina — 2024 General (Presidential)",
            "presets/nc_2024_pres.geojson.gz");

    /** All bundled presets, in display order. */
    public static List<Preset> all() {
        return List.of(NC_2024_PRESIDENTIAL);
    }
}
