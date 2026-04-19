package com.redistricting.io;

import com.redistricting.model.RedistrictingMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Loads a Dave's Redistricting <strong>Map Archive</strong> JSON export — the
 * roundtrip format produced by DRA's "Map Archive (.json)" option.
 *
 * <p>The archive's exact schema is undocumented and varies by version; this
 * loader looks for an embedded GeoJSON district-shapes payload (under common
 * keys such as {@code districtShapes}, {@code shapes}, {@code geojson}, or any
 * value that itself looks like a {@code FeatureCollection}) and delegates to
 * {@link DraGeoJsonLoader}. The archive's top-level {@code name} or
 * {@code mapName} is used as the plan name.
 */
public final class DraMapArchiveLoader {

    private DraMapArchiveLoader() {}

    public static RedistrictingMap loadFromFile(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return parse(text, fileNameStem(path.getFileName().toString()));
    }

    @SuppressWarnings("unchecked")
    public static RedistrictingMap parse(String json, String defaultName) {
        Object root = Json.parse(json);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("DRA Map Archive: top-level must be an object");
        }
        Map<String, Object> obj = (Map<String, Object>) root;

        String name = firstString(obj, List.of("name", "mapName", "title"), defaultName);

        Map<String, Object> features = findFeatureCollection(obj);
        if (features == null) {
            throw new IllegalArgumentException(
                    "DRA Map Archive: no embedded district-shapes FeatureCollection found");
        }
        return DraGeoJsonLoader.parse(toJsonStringApprox(features), name);
    }

    private static String firstString(Map<String, Object> obj, List<String> keys, String def) {
        for (String k : keys) {
            Object v = obj.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return def;
    }

    /** Recursively search for any object whose {@code type == "FeatureCollection"}. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> findFeatureCollection(Object node) {
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            if ("FeatureCollection".equals(map.get("type"))
                    && map.get("features") instanceof List<?>) {
                return map;
            }
            for (Object v : map.values()) {
                Map<String, Object> hit = findFeatureCollection(v);
                if (hit != null) return hit;
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                Map<String, Object> hit = findFeatureCollection(v);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    /**
     * Serialize the embedded FeatureCollection back to JSON so we can reuse
     * {@link DraGeoJsonLoader#parse}. Only handles the value shapes that our
     * own {@link Json} parser produces (Map / List / String / Number / Boolean /
     * null), which is exactly what we just round-tripped through.
     */
    @SuppressWarnings("unchecked")
    private static String toJsonStringApprox(Object node) {
        StringBuilder sb = new StringBuilder();
        writeJson(node, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeJson(Object node, StringBuilder sb) {
        if (node == null) { sb.append("null"); return; }
        if (node instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                writeJson(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (node instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                writeJson(l.get(i), sb);
            }
            sb.append(']');
            return;
        }
        if (node instanceof Number n) { sb.append(n.toString()); return; }
        if (node instanceof Boolean b) { sb.append(b.toString()); return; }
        writeString(String.valueOf(node), sb);
    }

    private static void writeString(String s, StringBuilder sb) {
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
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    private static String fileNameStem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // Touch StandardCharsets so static analysis doesn't flag it as unused on
    // platforms where Files.readString already handles encoding.
    @SuppressWarnings("unused")
    private static final java.nio.charset.Charset CHARSET = StandardCharsets.UTF_8;
}
