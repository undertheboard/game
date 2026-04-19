package com.redistricting.io;

import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Serializes a {@link RedistrictingMap} as a precinct-level
 * <strong>GeoJSON</strong> file.
 *
 * <p>The output is one {@code Feature} per precinct with these properties:
 * <ul>
 *   <li>{@code id} — the original precinct id</li>
 *   <li>{@code district} — 1-based district number (matches DRA's convention,
 *       so the file can be re-imported by DRA's "Plan from File" tools)</li>
 *   <li>{@code population}, {@code dem_votes}, {@code rep_votes}</li>
 * </ul>
 *
 * <p>Geometry is preserved exactly as loaded (single-ring → {@code Polygon},
 * multi-ring → {@code MultiPolygon}). Files written by this class round-trip
 * cleanly through {@link DraGeoJsonLoader}.
 */
public final class PlanGeoJsonWriter {

    private PlanGeoJsonWriter() {}

    public static void write(RedistrictingMap map, Path path) throws IOException {
        Files.writeString(path, toJson(map), StandardCharsets.UTF_8);
    }

    public static String toJson(RedistrictingMap map) {
        StringBuilder sb = new StringBuilder(64 * map.precincts().size());
        sb.append("{\n  \"type\": \"FeatureCollection\",\n");
        sb.append("  \"name\": ").append(jsonString(map.name())).append(",\n");
        sb.append("  \"features\": [\n");
        List<Precinct> ps = map.precincts();
        for (int i = 0; i < ps.size(); i++) {
            sb.append("    ").append(toFeature(ps.get(i)));
            if (i < ps.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static String toFeature(Precinct p) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"Feature\",\"properties\":{");
        sb.append("\"id\":").append(jsonString(p.id())).append(',');
        sb.append("\"district\":").append(p.district() + 1).append(',');
        sb.append("\"population\":").append(p.population()).append(',');
        sb.append("\"dem_votes\":").append(p.demVotes()).append(',');
        sb.append("\"rep_votes\":").append(p.repVotes());
        sb.append("},\"geometry\":");
        sb.append(geometry(p));
        sb.append('}');
        return sb.toString();
    }

    private static String geometry(Precinct p) {
        List<List<double[]>> rings = p.rings();
        StringBuilder sb = new StringBuilder();
        if (rings.size() == 1) {
            sb.append("{\"type\":\"Polygon\",\"coordinates\":[");
            sb.append(ringCoords(rings.get(0)));
            sb.append("]}");
        } else {
            sb.append("{\"type\":\"MultiPolygon\",\"coordinates\":[");
            for (int i = 0; i < rings.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append("[").append(ringCoords(rings.get(i))).append("]");
            }
            sb.append("]}");
        }
        return sb.toString();
    }

    private static String ringCoords(List<double[]> ring) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ring.size(); i++) {
            if (i > 0) sb.append(',');
            double[] v = ring.get(i);
            sb.append('[').append(num(v[0])).append(',').append(num(v[1])).append(']');
        }
        // Close the ring if it isn't already closed (GeoJSON convention).
        double[] first = ring.get(0);
        double[] last = ring.get(ring.size() - 1);
        if (first[0] != last[0] || first[1] != last[1]) {
            sb.append(',').append('[').append(num(first[0])).append(',')
                    .append(num(first[1])).append(']');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String num(double v) {
        // Compact, locale-invariant representation.
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        return String.format(Locale.ROOT, "%.8g", v);
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
