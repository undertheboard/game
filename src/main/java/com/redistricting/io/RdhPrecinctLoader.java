package com.redistricting.io;

import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Loaders for state precinct datasets distributed by the
 * <a href="https://redistrictingdatahub.org/">Redistricting Data Hub (RDH)</a>.
 *
 * <p>RDH precinct GeoJSON exports are functionally compatible with the DRA
 * District Shapes GeoJSON loader — they are GeoJSON {@code FeatureCollection}s
 * with one {@code Feature} per precinct, properties for population and
 * election totals, and {@code Polygon}/{@code MultiPolygon} geometry. We
 * delegate to {@link DraGeoJsonLoader#parse} for the heavy lifting and then
 * re-tag each resulting precinct as district 0 (so the loaded base is a
 * blank slate ready for an algorithm to redistrict).
 *
 * <p>RDH also distributes Block Equivalency Files (BEFs) — CSVs that map a
 * precinct id (or block id) to a district number. {@link #applyBef} applies
 * a BEF to an already-loaded precinct base.
 */
public final class RdhPrecinctLoader {

    private RdhPrecinctLoader() {}

    /** Load an RDH precinct GeoJSON file as a fresh, unassigned base map. */
    public static RedistrictingMap loadPrecinctsGeoJson(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return parsePrecinctsGeoJson(text, stem(path));
    }

    /**
     * Load a bundled precinct GeoJSON preset from the classpath. The
     * resource may optionally be gzip-compressed (path ending in
     * {@code .gz}) — gzipped resources are decompressed transparently so we
     * can ship large statewide precinct files inside the jar without
     * bloating its size.
     */
    public static RedistrictingMap loadPresetGeoJson(String resourcePath,
                                                     String displayName) throws IOException {
        ClassLoader cl = RdhPrecinctLoader.class.getClassLoader();
        try (InputStream raw = cl.getResourceAsStream(resourcePath)) {
            if (raw == null) {
                throw new IOException("preset resource not found: " + resourcePath);
            }
            try (InputStream in = resourcePath.toLowerCase(Locale.ROOT).endsWith(".gz")
                    ? new GZIPInputStream(raw) : raw) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                String text = buf.toString(StandardCharsets.UTF_8);
                return parsePrecinctsGeoJson(text, displayName);
            }
        }
    }

    private static RedistrictingMap parsePrecinctsGeoJson(String text, String name) {
        RedistrictingMap raw = DraGeoJsonLoader.parse(text, name);

        // Strip whatever district id was on each feature — RDH precinct files
        // carry per-precinct geometry and stats, not a redistricting plan.
        List<Precinct> stripped = new ArrayList<>(raw.precincts().size());
        for (Precinct p : raw.precincts()) {
            stripped.add(new Precinct(p.id(), 0, p.population(),
                    p.demVotes(), p.repVotes(), p.rings()));
        }
        return new RedistrictingMap("RDH precincts: " + raw.name(),
                /*districtCount until assigned*/ 1, stripped);
    }

    /**
     * Apply a Block Equivalency File (CSV) to an already-loaded precinct
     * base. The file must be a CSV with a header containing a precinct-id
     * column (any of: {@code id}, {@code precinct}, {@code geoid},
     * {@code GEOID20}, {@code block}, {@code BLOCKID}) and a district column
     * (any of: {@code district}, {@code dist}, {@code cd}, {@code DISTRICT}).
     *
     * <p>Precincts whose id is missing from the file keep their existing
     * district assignment. The returned map is renamed to reflect the BEF.
     */
    public static RedistrictingMap applyBef(RedistrictingMap base, Path csvPath)
            throws IOException {
        String csv = Files.readString(csvPath, StandardCharsets.UTF_8);
        return applyBef(base, csv, stem(csvPath));
    }

    public static RedistrictingMap applyBef(RedistrictingMap base, String csvText,
                                             String label) {
        List<List<String>> rows = Csv.parse(csvText);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("BEF: expected header + data rows");
        }
        List<String> header = rows.get(0);
        int idCol = findCol(header, List.of(
                "geoid20", "geoid", "precinct", "precinctid", "id",
                "blockid", "block"));
        int distCol = findCol(header, List.of(
                "district", "dist", "cd", "districtid", "district_no"));
        if (idCol < 0 || distCol < 0) {
            throw new IllegalArgumentException(
                    "BEF: need an id column and a district column. Saw: " + header);
        }

        Map<String, Integer> assignment = new HashMap<>();
        int maxDistrict = 0;
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.size() <= Math.max(idCol, distCol)) continue;
            String id = row.get(idCol).trim();
            Integer d = parseInt(row.get(distCol));
            if (id.isEmpty() || d == null) continue;
            assignment.put(id, d);
            maxDistrict = Math.max(maxDistrict, d);
        }

        List<Precinct> updated = new ArrayList<>(base.precincts().size());
        for (Precinct p : base.precincts()) {
            Integer d = assignment.get(p.id());
            int newDistrict = d == null ? p.district() : d - 1; // 1-based → 0-based
            updated.add(new Precinct(p.id(), newDistrict, p.population(),
                    p.demVotes(), p.repVotes(), p.rings()));
        }
        int districts = Math.max(maxDistrict,
                updated.stream().mapToInt(Precinct::district).max().orElse(0) + 1);
        return new RedistrictingMap(base.name() + " ← " + label, districts, updated);
    }

    private static int findCol(List<String> header, List<String> names) {
        for (String name : names) {
            for (int i = 0; i < header.size(); i++) {
                if (header.get(i).trim().toLowerCase(Locale.ROOT).equals(name)) return i;
            }
        }
        // Fall back to substring match.
        for (String name : names) {
            for (int i = 0; i < header.size(); i++) {
                if (header.get(i).toLowerCase(Locale.ROOT).contains(name)) return i;
            }
        }
        return -1;
    }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s.replace(",", "").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String stem(Path path) {
        String n = path.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
