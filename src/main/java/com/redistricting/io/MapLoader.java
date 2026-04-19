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
import java.util.Map;

/**
 * Loads a {@link RedistrictingMap} from a JSON file or classpath resource.
 *
 * <p>Expected JSON format:
 * <pre>{@code
 * {
 *   "name": "Sample State",
 *   "districts": 3,
 *   "precincts": [
 *     {
 *       "id": "P1",
 *       "district": 0,
 *       "population": 1000,
 *       "demVotes": 520,
 *       "repVotes": 480,
 *       "polygon": [[0,0],[10,0],[10,10],[0,10]]
 *     }
 *   ]
 * }
 * }</pre>
 */
public final class MapLoader {

    private MapLoader() {}

    public static RedistrictingMap loadFromFile(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return parse(text);
    }

    public static RedistrictingMap loadFromResource(String resourcePath) throws IOException {
        ClassLoader cl = MapLoader.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IOException("resource not found: " + resourcePath);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse(text);
        }
    }

    @SuppressWarnings("unchecked")
    public static RedistrictingMap parse(String json) {
        Object root = Json.parse(json);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("top-level JSON must be an object");
        }
        Map<String, Object> map = (Map<String, Object>) root;

        String name = (String) map.getOrDefault("name", "Unnamed");
        int districts = ((Number) require(map, "districts")).intValue();

        List<Object> rawPrecincts = (List<Object>) require(map, "precincts");
        List<Precinct> precincts = new ArrayList<>(rawPrecincts.size());
        for (Object o : rawPrecincts) {
            Map<String, Object> pm = (Map<String, Object>) o;
            String id = (String) require(pm, "id");
            int district = ((Number) require(pm, "district")).intValue();
            int population = ((Number) require(pm, "population")).intValue();
            int dem = ((Number) require(pm, "demVotes")).intValue();
            int rep = ((Number) require(pm, "repVotes")).intValue();
            List<Object> rawPoly = (List<Object>) require(pm, "polygon");
            List<double[]> ring = new ArrayList<>(rawPoly.size());
            for (Object v : rawPoly) {
                List<Object> pair = (List<Object>) v;
                if (pair.size() != 2) {
                    throw new IllegalArgumentException(
                            "polygon vertex must be [x,y] in precinct " + id);
                }
                ring.add(new double[] {
                        ((Number) pair.get(0)).doubleValue(),
                        ((Number) pair.get(1)).doubleValue() });
            }
            precincts.add(new Precinct(id, district, population, dem, rep, List.of(ring)));
        }
        return new RedistrictingMap(name, districts, precincts);
    }

    private static Object require(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException("missing field: " + key);
        }
        return map.get(key);
    }
}
