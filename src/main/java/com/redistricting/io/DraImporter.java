package com.redistricting.io;

import com.redistricting.model.RedistrictingMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Single entry point for "Import from Dave's Redistricting" — the user picks
 * any file produced by DRA's <em>Export Map to a File</em> dialog and this
 * class auto-detects the format and dispatches to the right loader.
 *
 * <p>Detection rules (in order):
 * <ol>
 *   <li>{@code .geojson} extension → {@link DraGeoJsonLoader}.</li>
 *   <li>{@code .csv}     extension → {@link DraDistrictDataCsvLoader} —
 *       requires a previously-loaded {@code base} map to enrich.</li>
 *   <li>{@code .json}    extension → {@link DraMapArchiveLoader}, falling back
 *       to {@link DraGeoJsonLoader} if the file is itself a FeatureCollection.</li>
 *   <li>If the extension is unknown, the first non-whitespace character is
 *       inspected: {@code '{'}/{@code '['} → JSON path, otherwise CSV path.</li>
 * </ol>
 */
public final class DraImporter {

    private DraImporter() {}

    /** Result of an import: either a fresh map, or a CSV that enriched {@code base}. */
    public record Result(RedistrictingMap map, String description) {}

    /**
     * @param path     the file the user selected from DRA's export dialog.
     * @param base     optionally, the currently-loaded map (used when the file
     *                 is a CSV that only enriches an existing plan); may be null.
     */
    public static Result importFile(Path path, RedistrictingMap base) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".geojson")) {
            return new Result(DraGeoJsonLoader.loadFromFile(path),
                    "Loaded DRA District Shapes GeoJSON: " + path.getFileName());
        }

        if (name.endsWith(".csv")) {
            if (base == null) {
                throw new IOException(
                        "DRA District Data CSV adds population/vote totals to an existing"
                        + " plan. Please open a District Shapes (.geojson) or Map Archive"
                        + " (.json) first, then enrich it with this CSV.");
            }
            return new Result(DraDistrictDataCsvLoader.enrich(base, path),
                    "Enriched plan with DRA District Data CSV: " + path.getFileName());
        }

        if (name.endsWith(".json")) {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            // A FeatureCollection-shaped .json (some DRA setups write GeoJSON with
            // a .json extension) — try the GeoJSON loader first; fall back to the
            // Map Archive loader.
            if (looksLikeFeatureCollection(text)) {
                return new Result(DraGeoJsonLoader.parse(text, stem(name)),
                        "Loaded DRA District Shapes (JSON): " + path.getFileName());
            }
            return new Result(DraMapArchiveLoader.parse(text, stem(name)),
                    "Loaded DRA Map Archive: " + path.getFileName());
        }

        // Unknown extension: sniff the contents.
        String text = Files.readString(path, StandardCharsets.UTF_8);
        String trimmed = text.stripLeading();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            if (looksLikeFeatureCollection(text)) {
                return new Result(DraGeoJsonLoader.parse(text, stem(name)),
                        "Loaded DRA District Shapes: " + path.getFileName());
            }
            return new Result(DraMapArchiveLoader.parse(text, stem(name)),
                    "Loaded DRA Map Archive: " + path.getFileName());
        }
        if (base == null) {
            throw new IOException("Unrecognised file format and no base map to enrich: "
                    + path.getFileName());
        }
        return new Result(DraDistrictDataCsvLoader.enrich(base, text),
                "Enriched plan with DRA CSV: " + path.getFileName());
    }

    private static boolean looksLikeFeatureCollection(String text) {
        // Cheap structural sniff — avoids a second full parse.
        return text.contains("\"FeatureCollection\"") && text.contains("\"features\"");
    }

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
