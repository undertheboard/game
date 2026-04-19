package com.redistricting.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helpers to convert GeoJSON {@code geometry} objects into the multi-ring
 * representation used by {@link com.redistricting.model.Precinct}.
 */
final class GeoJsonGeometry {

    private GeoJsonGeometry() {}

    @SuppressWarnings("unchecked")
    static List<List<double[]>> toRings(Map<String, Object> geom) {
        String type = String.valueOf(geom.get("type"));
        Object coords = geom.get("coordinates");
        List<List<double[]>> out = new ArrayList<>();
        switch (type) {
            case "Polygon" -> {
                // coordinates: [ ring, hole, hole, ... ] — keep outer only.
                List<Object> rings = (List<Object>) coords;
                if (!rings.isEmpty()) out.add(toRing((List<Object>) rings.get(0)));
            }
            case "MultiPolygon" -> {
                // coordinates: [ polygon, polygon, ... ] each polygon = [ring, holes...]
                for (Object poly : (List<Object>) coords) {
                    List<Object> rings = (List<Object>) poly;
                    if (!rings.isEmpty()) out.add(toRing((List<Object>) rings.get(0)));
                }
            }
            default -> throw new IllegalArgumentException(
                    "unsupported geometry type: " + type);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<double[]> toRing(List<Object> raw) {
        List<double[]> ring = new ArrayList<>(raw.size());
        for (Object pt : raw) {
            List<Object> coord = (List<Object>) pt;
            ring.add(new double[] {
                    ((Number) coord.get(0)).doubleValue(),
                    ((Number) coord.get(1)).doubleValue() });
        }
        // GeoJSON rings are closed (first == last); drop the duplicate.
        if (ring.size() > 3) {
            double[] a = ring.get(0);
            double[] b = ring.get(ring.size() - 1);
            if (a[0] == b[0] && a[1] == b[1]) ring.remove(ring.size() - 1);
        }
        return ring;
    }
}
