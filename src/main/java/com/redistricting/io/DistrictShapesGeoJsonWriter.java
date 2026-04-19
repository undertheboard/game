package com.redistricting.io;

import com.redistricting.model.District;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Serializes a {@link RedistrictingMap} as a <strong>district-level</strong>
 * GeoJSON file — one {@code Feature} per district, with each district's
 * geometry expressed as a {@code MultiPolygon} that simply concatenates the
 * rings of every precinct assigned to it.
 *
 * <p>This is the format DRA emits for its <i>District Shapes</i> export and
 * is the most convenient artefact for users who want to drop their plan into
 * QGIS, ArcGIS, or any other GIS pipeline that doesn't need precinct-level
 * detail. Use {@link PlanGeoJsonWriter} when you want a precinct-level
 * round-trippable GeoJSON instead.
 *
 * <p>Because we don't link in a polygon-union library, the per-district
 * geometry is the union of <em>rings</em>, not a topologically dissolved
 * polygon. All major GIS tools accept this representation.
 */
public final class DistrictShapesGeoJsonWriter {

    private DistrictShapesGeoJsonWriter() {}

    public static void write(RedistrictingMap map, Path path) throws IOException {
        Files.writeString(path, toJson(map), StandardCharsets.UTF_8);
    }

    public static String toJson(RedistrictingMap map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"type\": \"FeatureCollection\",\n");
        sb.append("  \"name\": ").append(jsonString(map.name() + " — districts")).append(",\n");
        sb.append("  \"features\": [\n");
        List<District> ds = map.districts();
        for (int i = 0; i < ds.size(); i++) {
            sb.append("    ").append(toFeature(ds.get(i)));
            if (i < ds.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static String toFeature(District d) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"Feature\",\"properties\":{");
        sb.append("\"district\":").append(d.id() + 1).append(',');
        sb.append("\"population\":").append(d.totalPopulation()).append(',');
        sb.append("\"dem_votes\":").append(d.totalDemVotes()).append(',');
        sb.append("\"rep_votes\":").append(d.totalRepVotes());
        sb.append("},\"geometry\":");
        sb.append(geometry(d));
        sb.append('}');
        return sb.toString();
    }

    private static String geometry(District d) {
        StringBuilder sb = new StringBuilder("{\"type\":\"MultiPolygon\",\"coordinates\":[");
        boolean first = true;
        for (Precinct p : d.precincts()) {
            for (List<double[]> ring : p.rings()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('[').append(ringCoords(ring)).append(']');
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String ringCoords(List<double[]> ring) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ring.size(); i++) {
            if (i > 0) sb.append(',');
            double[] v = ring.get(i);
            sb.append('[').append(num(v[0])).append(',').append(num(v[1])).append(']');
        }
        double[] firstV = ring.get(0);
        double[] last = ring.get(ring.size() - 1);
        if (firstV[0] != last[0] || firstV[1] != last[1]) {
            sb.append(',').append('[').append(num(firstV[0])).append(',')
                    .append(num(firstV[1])).append(']');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String num(double v) {
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
