package com.redistricting.io;

import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Enriches an already-loaded {@link RedistrictingMap} with population and
 * vote totals from a Dave's Redistricting <strong>District Data</strong>
 * CSV export. Header names are matched case-insensitively by substring, so
 * different DRA export variants are tolerated.
 *
 * <p>Required column: a district identifier — any header containing
 * "district". Recognised data columns:
 * <ul>
 *   <li>Population — header containing both "total" and "pop", or "total_2020".</li>
 *   <li>Dem / Rep / Other votes — most-recent presidential triplet, e.g.
 *       {@code 2024 President Dem}, {@code Pres_Dem}.</li>
 * </ul>
 */
public final class DraDistrictDataCsvLoader {

    private DraDistrictDataCsvLoader() {}

    public static RedistrictingMap enrich(RedistrictingMap base, Path csvPath) throws IOException {
        return enrich(base, Files.readString(csvPath, StandardCharsets.UTF_8));
    }

    public static RedistrictingMap enrich(RedistrictingMap base, String csvText) {
        List<List<String>> rows = Csv.parse(csvText);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("DRA CSV: expected header + data rows");
        }
        List<String> header = rows.get(0);
        int districtCol = findColumn(header, List.of("district"), -1);
        int popCol = findColumn(header, List.of("total_2020_total", "total_pop", "totpop"),
                findCombinedColumn(header, "total", "pop"));
        int demCol = findColumn(header, List.of(
                "2024_pres_dem", "2024 pres dem", "pres_2024_dem", "_dem", "dem"), -1);
        int repCol = findColumn(header, List.of(
                "2024_pres_rep", "2024 pres rep", "pres_2024_rep", "_rep", "rep"), -1);

        if (districtCol < 0) {
            throw new IllegalArgumentException("DRA CSV: no 'district' column found");
        }

        // district id (1-based as in DRA) -> [pop, dem, rep]
        Map<Integer, int[]> byDistrict = new HashMap<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.size() <= districtCol) continue;
            Integer d = parseInt(row.get(districtCol));
            if (d == null) continue;
            int pop = popCol >= 0 && popCol < row.size() ? parseIntOr(row.get(popCol), 0) : 0;
            int dem = demCol >= 0 && demCol < row.size() ? parseIntOr(row.get(demCol), 0) : 0;
            int rep = repCol >= 0 && repCol < row.size() ? parseIntOr(row.get(repCol), 0) : 0;
            byDistrict.put(d, new int[] { pop, dem, rep });
        }

        // Replace each precinct with one carrying the enriched stats.
        List<Precinct> updated = new ArrayList<>(base.precincts().size());
        for (Precinct p : base.precincts()) {
            int draId = p.district() + 1;
            int[] stats = byDistrict.get(draId);
            if (stats == null) {
                updated.add(p);
            } else {
                updated.add(new Precinct(p.id(), p.district(),
                        stats[0], stats[1], stats[2], p.rings()));
            }
        }
        return new RedistrictingMap(base.name(), base.districtCount(), updated);
    }

    private static int findColumn(List<String> header, List<String> hints, int fallback) {
        for (String hint : hints) {
            String h = hint.toLowerCase(Locale.ROOT);
            for (int i = 0; i < header.size(); i++) {
                if (header.get(i).toLowerCase(Locale.ROOT).contains(h)) return i;
            }
        }
        return fallback;
    }

    private static int findCombinedColumn(List<String> header, String a, String b) {
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).toLowerCase(Locale.ROOT);
            if (h.contains(a) && h.contains(b)) return i;
        }
        return -1;
    }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s.replace(",", "").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static int parseIntOr(String s, int fallback) {
        Integer v = parseInt(s);
        return v == null ? fallback : v;
    }
}
