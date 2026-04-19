package com.redistricting.io;

import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads a {@link RedistrictingMap} from a <strong>Dave's Redistricting App
 * (DRA)</strong> "District Shapes" GeoJSON export (one Feature per district).
 *
 * <p>DRA's property names vary slightly between exports, so this loader
 * matches them case-insensitively by substring. Recognised properties:
 * <ul>
 *   <li>District ID — any of {@code District}, {@code DISTRICT}, {@code Dist},
 *       {@code CD}, {@code DistrictID}.</li>
 *   <li>Population — first property whose name matches {@code Total_2020_Total},
 *       {@code Total Pop *}, {@code Pop_*}, or contains both "total" and "pop".</li>
 *   <li>Dem / Rep / Other votes — the most-recent presidential pair, e.g.
 *       {@code 2024_Pres_Dem} / {@code 2024_Pres_Rep} / {@code 2024_Pres_Oth}.
 *       Falls back to any property containing "Dem"/"Rep"/"Oth".</li>
 * </ul>
 * Each district feature becomes a single {@link Precinct} assigned to its own
 * district (precinct-level data isn't included in this DRA export). To enable
 * the AI optimiser, additionally load a precinct-level dataset.
 *
 * <p>Geometries may be {@code Polygon} or {@code MultiPolygon} — both are
 * preserved as multi-ring precincts.
 */
public final class DraGeoJsonLoader {

    private DraGeoJsonLoader() {}

    public static RedistrictingMap loadFromFile(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8),
                fileNameStem(path.getFileName().toString()));
    }

    public static RedistrictingMap loadFromResource(String resourcePath) throws IOException {
        ClassLoader cl = DraGeoJsonLoader.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IOException("resource not found: " + resourcePath);
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    fileNameStem(resourcePath));
        }
    }

    @SuppressWarnings("unchecked")
    public static RedistrictingMap parse(String json, String defaultName) {
        Object root = Json.parse(json);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("DRA GeoJSON: top-level must be an object");
        }
        Map<String, Object> obj = (Map<String, Object>) root;
        String type = String.valueOf(obj.get("type"));
        if (!"FeatureCollection".equals(type)) {
            throw new IllegalArgumentException(
                    "DRA GeoJSON: expected FeatureCollection, got " + type);
        }
        List<Object> features = (List<Object>) obj.get("features");
        if (features == null || features.isEmpty()) {
            throw new IllegalArgumentException("DRA GeoJSON: no features");
        }

        String name = (String) obj.getOrDefault("name", defaultName);

        List<Precinct> precincts = new ArrayList<>(features.size());
        int autoId = 1;
        int maxDistrict = 0;
        for (Object f : features) {
            Map<String, Object> feature = (Map<String, Object>) f;
            Map<String, Object> props = (Map<String, Object>) feature.getOrDefault(
                    "properties", Map.of());
            Map<String, Object> geom = (Map<String, Object>) feature.get("geometry");
            if (geom == null) continue;

            int district = readDistrict(props, autoId);
            autoId = Math.max(autoId, district) + 1;
            maxDistrict = Math.max(maxDistrict, district);

            int population = (int) Math.round(readNumber(props, POP_HINTS, 0));
            int dem = (int) Math.round(readNumber(props, DEM_HINTS, 0));
            int rep = (int) Math.round(readNumber(props, REP_HINTS, 0));

            List<List<double[]>> rings = GeoJsonGeometry.toRings(geom);
            if (rings.isEmpty()) continue;

            // DRA district IDs start at 1; our internal districts are 0-indexed.
            // Preserve the real precinct id when the source file carries one
            // (RDH, our own writer, etc.); otherwise fall back to "D{n}" so
            // legacy DRA District Shapes exports keep their old behaviour.
            String precinctId = readPrecinctId(props);
            if (precinctId == null || precinctId.isBlank()) {
                precinctId = "D" + district;
            }
            precincts.add(new Precinct(
                    precinctId, district - 1, population, dem, rep, rings));
        }

        if (precincts.isEmpty()) {
            throw new IllegalArgumentException("DRA GeoJSON: no usable features");
        }
        return new RedistrictingMap(name, maxDistrict, precincts);
    }

    private static String readPrecinctId(Map<String, Object> props) {
        // Common RDH / Census / DRA id property names, case-insensitive.
        for (String key : props.keySet()) {
            String lk = key.toLowerCase(Locale.ROOT);
            if (lk.equals("id") || lk.equals("precinct") || lk.equals("precinctid")
                    || lk.equals("geoid") || lk.equals("geoid20") || lk.equals("geoid10")
                    || lk.equals("vtdid") || lk.equals("vtdst20") || lk.equals("uniqueid")) {
                Object v = props.get(key);
                if (v != null) return String.valueOf(v).trim();
            }
        }
        return null;
    }

    private static int readDistrict(Map<String, Object> props, int fallback) {
        for (String key : props.keySet()) {
            String lk = key.toLowerCase(Locale.ROOT);
            if (lk.equals("district") || lk.equals("districtid")
                    || lk.equals("dist") || lk.equals("cd")
                    || lk.equals("district_id") || lk.equals("district_no")) {
                Object v = props.get(key);
                if (v instanceof Number n) return n.intValue();
                if (v instanceof String s) {
                    try { return Integer.parseInt(s.trim()); }
                    catch (NumberFormatException ignored) { }
                }
            }
        }
        return fallback;
    }

    private static double readNumber(Map<String, Object> props,
                                     List<String> nameHints, double fallback) {
        for (String hint : nameHints) {
            for (String key : props.keySet()) {
                if (key.toLowerCase(Locale.ROOT).contains(hint)) {
                    Object v = props.get(key);
                    if (v instanceof Number n) return n.doubleValue();
                    if (v instanceof String s) {
                        try { return Double.parseDouble(s.replace(",", "").trim()); }
                        catch (NumberFormatException ignored) { }
                    }
                }
            }
        }
        return fallback;
    }

    private static String fileNameStem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // Most-specific to least-specific so we prefer 2024 presidential when present.
    private static final List<String> POP_HINTS = List.of(
            "total_2020_total", "total_pop_2020", "totpop", "total_pop", "population");
    private static final List<String> DEM_HINTS = List.of(
            "2024_pres_dem", "2024_president_dem", "pres_2024_dem",
            "2020_pres_dem", "pres_dem", "_dem", "dem");
    private static final List<String> REP_HINTS = List.of(
            "2024_pres_rep", "2024_president_rep", "pres_2024_rep",
            "2020_pres_rep", "pres_rep", "_rep", "rep");
}
