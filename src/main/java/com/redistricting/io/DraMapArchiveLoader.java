package com.redistricting.io;

import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Loads a Dave's Redistricting <strong>Map Archive</strong> JSON export — the
 * roundtrip format produced by DRA's "Map Archive (.json)" option.
 *
 * <p>The archive's exact schema is undocumented and varies by DRA version, but
 * in practice it falls into one of two shapes:
 * <ol>
 *   <li><strong>Embedded GeoJSON</strong> — some exports carry a full
 *       district-shapes {@code FeatureCollection} under a key such as
 *       {@code districtShapes} / {@code shapes} / {@code geojson}. In that
 *       case we delegate to {@link DraGeoJsonLoader}.</li>
 *   <li><strong>Assignment-only</strong> — the common case. DRA stores a
 *       precinct-id → district-number map (typical keys: {@code plan},
 *       {@code assignments}, {@code blockAssignments}) and references its
 *       own dataset for geometry. Since we can't fetch DRA's geometry
 *       offline, we build a placeholder per-district grid so the plan loads
 *       and can be enriched later (e.g. via the District Data CSV).</li>
 * </ol>
 * The archive's top-level {@code name} / {@code mapName} / {@code title} (when
 * present) becomes the plan name.
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

        String name = firstString(obj, List.of("name", "mapName", "title", "planName"),
                defaultName);

        // Preferred path: embedded GeoJSON district shapes.
        Map<String, Object> features = findFeatureCollection(obj);
        if (features != null) {
            return DraGeoJsonLoader.parse(toJsonStringApprox(features), name);
        }

        // Fallback: assignment-only archive. Build a placeholder per-district map.
        Map<String, Object> assignments = findAssignmentMap(obj);
        if (assignments != null && !assignments.isEmpty()) {
            return synthesiseFromAssignments(name, assignments);
        }

        throw new IllegalArgumentException(
                "DRA Map Archive: no embedded district shapes and no recognisable"
                + " precinct-to-district assignment map found");
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
     * Recursively search for a precinct-id → district map. DRA archives use
     * several names ({@code plan}, {@code assignments}, {@code blockAssignments},
     * {@code districtAssignments}) so we look at well-known keys first and then
     * fall back to any nested object that <em>looks like</em> an assignment
     * map: a {@code Map<String,?>} whose values are predominantly small
     * non-negative integers (district numbers).
     *
     * <p>Also accepts the array-of-pairs form
     * {@code [{"id": "...", "district": 3}, ...]}.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> findAssignmentMap(Object node) {
        // Pass 1: look for a well-known key.
        List<String> wellKnown = List.of(
                "plan", "assignments", "blockAssignments", "districtAssignments",
                "precinctAssignments", "BlockAssignments");
        Map<String, Object> hit = findByKeys(node, wellKnown);
        if (hit != null) return hit;
        // Pass 2: any nested map that looks like an assignment.
        return findHeuristicAssignment(node);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findByKeys(Object node, List<String> keys) {
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            for (String k : keys) {
                Object v = map.get(k);
                Map<String, Object> coerced = coerceAssignmentMap(v);
                if (coerced != null) return coerced;
            }
            for (Object v : map.values()) {
                Map<String, Object> nested = findByKeys(v, keys);
                if (nested != null) return nested;
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                Map<String, Object> nested = findByKeys(v, keys);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findHeuristicAssignment(Object node) {
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            if (looksLikeAssignmentDict(map)) return map;
            Map<String, Object> coerced = coerceAssignmentMap(map);
            // (a list-of-pairs masquerading as a value won't match here, but
            // coerceAssignmentMap may still succeed on nested values below)
            if (coerced != null && coerced != map) return coerced;
            for (Object v : map.values()) {
                Map<String, Object> nested = findHeuristicAssignment(v);
                if (nested != null) return nested;
            }
        } else if (node instanceof List<?> list) {
            Map<String, Object> coerced = coerceAssignmentMap(list);
            if (coerced != null) return coerced;
            for (Object v : list) {
                Map<String, Object> nested = findHeuristicAssignment(v);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    /**
     * Return {@code value} as a {String → district-number} map if it can be
     * interpreted as one, otherwise {@code null}. Accepts:
     * <ul>
     *   <li>{@code Map<String, Number|String>} where most values parse as
     *       small non-negative integers.</li>
     *   <li>{@code List<Map>} of {@code {id|precinct|geoid|block: ...,
     *       district|cd|dist: ...}} pairs.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> coerceAssignmentMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            return looksLikeAssignmentDict(map) ? map : null;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return null;
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) return null;
                Map<String, Object> entry = (Map<String, Object>) m;
                String id = pairId(entry);
                Object district = pairDistrict(entry);
                if (id == null || district == null) return null;
                out.put(id, district);
            }
            return out.isEmpty() ? null : out;
        }
        return null;
    }

    private static String pairId(Map<String, Object> entry) {
        for (String k : List.of("id", "ID", "Id", "precinct", "precinctId",
                                 "geoid", "GEOID", "block", "blockId")) {
            Object v = entry.get(k);
            if (v != null) return String.valueOf(v);
        }
        return null;
    }

    private static Object pairDistrict(Map<String, Object> entry) {
        for (String k : List.of("district", "District", "DISTRICT",
                                 "dist", "Dist", "cd", "CD", "districtId")) {
            Object v = entry.get(k);
            if (v != null) return v;
        }
        return null;
    }

    /**
     * A map "looks like" an assignment dict when at least 80% of its values
     * are integers in [0, 200] (district numbers; we cap to keep large
     * numeric metadata maps from being misidentified) and it has at least 2
     * entries.
     */
    private static boolean looksLikeAssignmentDict(Map<String, Object> map) {
        if (map.size() < 2) return false;
        // Reject obvious non-assignment shapes early.
        if (map.containsKey("type") && map.get("type") instanceof String) return false;
        if (map.containsKey("features") || map.containsKey("geometry")) return false;
        int total = 0, ok = 0;
        for (Object v : map.values()) {
            total++;
            Integer i = asSmallInt(v);
            if (i != null && i >= 0 && i <= 200) ok++;
        }
        return total > 0 && ok * 5 >= total * 4; // ≥80%
    }

    private static Integer asSmallInt(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) return (int) d;
        }
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    /**
     * Build a placeholder map: one square precinct per <em>distinct</em>
     * district number found in {@code assignments}, laid out in a near-square
     * grid, populated with synthetic equal populations and zero votes. This
     * lets the user view and later enrich an assignment-only archive.
     */
    private static RedistrictingMap synthesiseFromAssignments(String name,
            Map<String, Object> assignments) {
        // Collect distinct district numbers, normalising to 0-indexed contiguous IDs.
        TreeSet<Integer> raw = new TreeSet<>();
        // Count precincts per district for synthetic population weighting.
        java.util.HashMap<Integer, Integer> popCount = new java.util.HashMap<>();
        for (Object v : assignments.values()) {
            Integer d = asSmallInt(v);
            if (d == null) continue;
            raw.add(d);
            popCount.merge(d, 1, Integer::sum);
        }
        if (raw.isEmpty()) {
            throw new IllegalArgumentException(
                    "DRA Map Archive: assignment map contains no district numbers");
        }
        // Map original district number → 0-based ordinal.
        java.util.HashMap<Integer, Integer> ordinal = new java.util.HashMap<>();
        int next = 0;
        for (int d : raw) ordinal.put(d, next++);
        int districts = ordinal.size();

        int side = (int) Math.ceil(Math.sqrt(districts));
        List<Precinct> precincts = new ArrayList<>(districts);
        for (int origDistrict : raw) {
            int ord = ordinal.get(origDistrict);
            int x = ord % side;
            int y = ord / side;
            int precincts_in_district = popCount.getOrDefault(origDistrict, 1);
            // Synthetic population proportional to # of precincts assigned
            // (gives a hint at relative district sizes when known).
            int pop = Math.max(1, precincts_in_district * 100);
            List<double[]> ring = List.of(
                    new double[] { x, y },
                    new double[] { x + 1, y },
                    new double[] { x + 1, y + 1 },
                    new double[] { x, y + 1 });
            precincts.add(new Precinct("D" + origDistrict, ord, pop, 0, 0, List.of(ring)));
        }
        String displayName = name + " (assignments only — placeholder geometry)";
        return new RedistrictingMap(displayName, districts, precincts);
    }

    /**
     * Serialize the embedded FeatureCollection back to JSON so we can reuse
     * {@link DraGeoJsonLoader#parse}. Only handles the value shapes that our
     * own {@link Json} parser produces (Map / List / String / Number / Boolean /
     * null), which is exactly what we just round-tripped through.
     */
    private static String toJsonStringApprox(Object node) {
        StringBuilder sb = new StringBuilder();
        writeJson(node, sb);
        return sb.toString();
    }

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
                    if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
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
}
