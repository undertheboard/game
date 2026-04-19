package com.redistricting.gui;

import com.redistricting.model.District;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A pannable / zoomable Swing canvas that draws a {@link RedistrictingMap}.
 *
 * <p>Three independent view toggles are exposed:
 * <ul>
 *   <li>{@link ViewMode#DISTRICT} colours each precinct by its district id
 *       (default), or {@link ViewMode#PARTISAN_LEAN} shades it from deep
 *       red (Republican) through grey (50/50) to deep blue (Democratic)
 *       based on the precinct or district Dem-share.</li>
 *   <li>{@link #setShowPrecinctLines(boolean)} draws thin outlines around
 *       every precinct (helpful for inspecting boundaries) — toggle off for
 *       a cleaner display.</li>
 *   <li>{@link #setShowDistrictNumbers(boolean)} stamps each district's
 *       1-based number at its population-weighted centroid.</li>
 * </ul>
 *
 * <p>Tooltips expose precinct details. Drag to pan, scroll to zoom.
 */
public final class MapPanel extends JPanel {

    /** Colouring scheme for the map fill. */
    public enum ViewMode { DISTRICT, PARTISAN_LEAN }

    private RedistrictingMap map;
    private double scale = 1.0;
    private double offsetX = 0, offsetY = 0;
    private int lastDragX, lastDragY;
    private double[] bbox;

    private ViewMode viewMode = ViewMode.DISTRICT;
    private boolean showPrecinctLines = true;
    private boolean showDistrictLines = true;
    private boolean showDistrictNumbers = true;
    private boolean dark = false;

    /** Pre-computed boundary segments between precincts of different districts. */
    private List<double[]> districtBoundary; // each entry: {x1,y1,x2,y2}
    /** Pre-computed population-weighted district centroids for label placement. */
    private Point2D.Double[] districtCentroid;

    public MapPanel() {
        setBackground(new Color(0xF5F5F5));
        setPreferredSize(new Dimension(900, 650));
        setToolTipText("");

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                lastDragX = e.getX();
                lastDragY = e.getY();
            }
            @Override public void mouseDragged(MouseEvent e) {
                offsetX += e.getX() - lastDragX;
                offsetY += e.getY() - lastDragY;
                lastDragX = e.getX();
                lastDragY = e.getY();
                repaint();
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = Math.pow(1.1, -e.getPreciseWheelRotation());
                offsetX = e.getX() - (e.getX() - offsetX) * factor;
                offsetY = e.getY() - (e.getY() - offsetY) * factor;
                scale *= factor;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public void setDarkMode(boolean dark) {
        this.dark = dark;
        setBackground(dark ? DarkTheme.BACKGROUND : new Color(0xF5F5F5));
        repaint();
    }

    public void setMap(RedistrictingMap map) {
        this.map = map;
        this.bbox = computeBbox(map);
        this.districtBoundary = computeDistrictBoundary(map);
        this.districtCentroid = computeDistrictCentroids(map);
        resetView();
        repaint();
    }

    public RedistrictingMap getMap() { return map; }

    public ViewMode getViewMode() { return viewMode; }
    public void setViewMode(ViewMode m) {
        if (m != null && m != viewMode) { this.viewMode = m; repaint(); }
    }

    public boolean isShowPrecinctLines() { return showPrecinctLines; }
    public void setShowPrecinctLines(boolean v) {
        if (v != showPrecinctLines) { showPrecinctLines = v; repaint(); }
    }

    public boolean isShowDistrictLines() { return showDistrictLines; }
    public void setShowDistrictLines(boolean v) {
        if (v != showDistrictLines) { showDistrictLines = v; repaint(); }
    }

    public boolean isShowDistrictNumbers() { return showDistrictNumbers; }
    public void setShowDistrictNumbers(boolean v) {
        if (v != showDistrictNumbers) { showDistrictNumbers = v; repaint(); }
    }

    public void resetView() {
        if (bbox == null) return;
        double w = bbox[2] - bbox[0];
        double h = bbox[3] - bbox[1];
        if (w <= 0 || h <= 0) return;
        Dimension size = getSize();
        if (size.width == 0 || size.height == 0) size = getPreferredSize();
        double sx = (size.width - 40) / w;
        double sy = (size.height - 40) / h;
        scale = Math.min(sx, sy);
        offsetX = 20 - bbox[0] * scale;
        offsetY = size.height - 20 + bbox[1] * scale;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (map == null) return null;
        double mx = (e.getX() - offsetX) / scale;
        double my = -(e.getY() - offsetY) / scale;
        for (Precinct p : map.precincts()) {
            for (List<double[]> ring : p.rings()) {
                if (pointInRing(mx, my, ring)) {
                    int total = p.demVotes() + p.repVotes();
                    String lean = total == 0 ? "—"
                            : String.format("%.1f%% Dem", 100.0 * p.demVotes() / total);
                    return String.format(
                            "<html><b>%s</b><br/>District: %d<br/>"
                            + "Pop: %,d<br/>Dem: %,d &nbsp;Rep: %,d &nbsp;(%s)</html>",
                            p.id(), p.district() + 1, p.population(),
                            p.demVotes(), p.repVotes(), lean);
                }
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (map == null) {
            g.setColor(dark ? DarkTheme.FOREGROUND_DIM : Color.DARK_GRAY);
            g.drawString("No plan loaded.", 20, 30);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform t = new AffineTransform();
        t.translate(offsetX, offsetY);
        t.scale(scale, -scale);

        Color[] palette = paletteFor(map.districtCount(), dark);
        // Pre-compute per-district lean for the partisan view.
        double[] districtLean = districtLeans(map);

        // Fill precincts.
        for (Precinct p : map.precincts()) {
            Color fill = (viewMode == ViewMode.PARTISAN_LEAN)
                    ? leanColor(precinctLean(p, districtLean), dark)
                    : palette[Math.floorMod(p.district(), palette.length)];
            g2.setColor(fill);
            Path2D.Double path = polyPath(p);
            g2.fill(path.createTransformedShape(t));
        }

        // Optional thin precinct outlines.
        if (showPrecinctLines) {
            g2.setStroke(new BasicStroke(0.5f));
            g2.setColor(dark ? new Color(255, 255, 255, 60) : new Color(0, 0, 0, 80));
            for (Precinct p : map.precincts()) {
                Path2D.Double path = polyPath(p);
                g2.draw(path.createTransformedShape(t));
            }
        }

        // District boundaries — thicker, contrasting; toggleable from View menu.
        if (showDistrictLines) {
            g2.setStroke(new BasicStroke(1.8f));
            g2.setColor(dark ? DarkTheme.MAP_BOUNDARY : Color.BLACK);
            if (districtBoundary != null) {
                for (double[] seg : districtBoundary) {
                    Path2D.Double line = new Path2D.Double();
                    line.moveTo(seg[0], seg[1]);
                    line.lineTo(seg[2], seg[3]);
                    g2.draw(line.createTransformedShape(t));
                }
            }
        }

        // District-number labels.
        if (showDistrictNumbers && districtCentroid != null) {
            g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
            for (int d = 0; d < districtCentroid.length; d++) {
                Point2D.Double c = districtCentroid[d];
                if (c == null) continue;
                Point2D.Double pt = new Point2D.Double();
                t.transform(c, pt);
                drawOutlinedString(g2, String.valueOf(d + 1),
                        (int) pt.x, (int) pt.y, dark);
            }
        }

        drawLegend(g2, palette, districtLean);
        g2.dispose();
    }

    private void drawOutlinedString(Graphics2D g2, String s, int x, int y, boolean dark) {
        Color halo = dark ? Color.BLACK : Color.WHITE;
        Color text = dark ? Color.WHITE : Color.BLACK;
        // Crude outline: 8-direction halo.
        g2.setColor(halo);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                g2.drawString(s, x + dx, y + dy);
            }
        }
        g2.setColor(text);
        g2.drawString(s, x, y);
    }

    private void drawLegend(Graphics2D g2, Color[] palette, double[] districtLean) {
        if (map == null) return;
        int x = 12, y = 12, sw = 18, sh = 14, pad = 4;
        Color box = dark ? new Color(30, 30, 36, 220) : new Color(255, 255, 255, 220);
        Color text = dark ? DarkTheme.FOREGROUND : Color.DARK_GRAY;
        Color line = dark ? DarkTheme.BORDER : Color.BLACK;

        int rows = map.districtCount();
        g2.setColor(box);
        g2.fillRoundRect(x - 4, y - 4, 220, rows * (sh + pad) + 24, 8, 8);
        g2.setColor(text);
        g2.drawString(map.name(), x, y + 12);
        y += 18;
        for (District d : map.districts()) {
            Color swatch = (viewMode == ViewMode.PARTISAN_LEAN)
                    ? leanColor(districtLean[d.id()], dark)
                    : palette[Math.floorMod(d.id(), palette.length)];
            g2.setColor(swatch);
            g2.fillRect(x, y, sw, sh);
            g2.setColor(line);
            g2.drawRect(x, y, sw, sh);
            g2.setColor(text);
            String leanLabel = Double.isNaN(districtLean[d.id()]) ? "—"
                    : String.format("%.0f%% D", 100 * districtLean[d.id()]);
            g2.drawString(String.format("D%d  pop %,d  %s",
                    d.id() + 1, d.totalPopulation(), leanLabel),
                    x + sw + 6, y + sh - 2);
            y += sh + pad;
        }
    }

    // ---------- per-precinct / per-district lean -------------------------

    private double precinctLean(Precinct p, double[] districtLean) {
        int total = p.demVotes() + p.repVotes();
        if (total > 0) return (double) p.demVotes() / total;
        // Fall back to the district-level lean if the precinct has no votes
        // (typical for RDH precincts that haven't yet been enriched).
        double dl = districtLean[Math.max(0, Math.min(districtLean.length - 1, p.district()))];
        return Double.isNaN(dl) ? 0.5 : dl;
    }

    private double[] districtLeans(RedistrictingMap map) {
        double[] leans = new double[map.districtCount()];
        long[] dem = new long[map.districtCount()];
        long[] rep = new long[map.districtCount()];
        for (Precinct p : map.precincts()) {
            int d = p.district();
            if (d < 0 || d >= map.districtCount()) continue;
            dem[d] += p.demVotes();
            rep[d] += p.repVotes();
        }
        for (int i = 0; i < leans.length; i++) {
            long t = dem[i] + rep[i];
            leans[i] = t == 0 ? Double.NaN : (double) dem[i] / t;
        }
        return leans;
    }

    /** Map a Dem-share in [0,1] to a red→grey→blue gradient. */
    private static Color leanColor(double share, boolean dark) {
        if (Double.isNaN(share)) return dark ? new Color(0x40, 0x40, 0x48)
                                             : new Color(0xC8, 0xC8, 0xC8);
        share = Math.max(0, Math.min(1, share));
        Color rep = new Color(0xD7, 0x2F, 0x2F);
        Color mid = dark ? new Color(0x80, 0x80, 0x88) : new Color(0xE5, 0xE5, 0xE5);
        Color dem = new Color(0x1F, 0x55, 0xCC);
        double t = (share - 0.5) * 2.0; // -1..+1
        if (t < 0) return blend(rep, mid, 1 + t);  // -1..0
        return blend(mid, dem, t);                 //  0..+1
    }

    private static Color blend(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(a.getRed()   * (1 - t) + b.getRed()   * t);
        int g = (int) Math.round(a.getGreen() * (1 - t) + b.getGreen() * t);
        int bl = (int) Math.round(a.getBlue()  * (1 - t) + b.getBlue()  * t);
        return new Color(r, g, bl);
    }

    // ---------- precomputed boundary + centroids -------------------------

    /**
     * Compute the line segments that lie between precincts of different
     * districts. Two precincts are considered to share an edge when they
     * share two consecutive vertices (within a small tolerance) on their
     * rings — this is exact for all geometries that came from a real GIS
     * dataset (where shared boundaries use shared vertex coordinates).
     */
    private static List<double[]> computeDistrictBoundary(RedistrictingMap map) {
        if (map == null) return List.of();
        Map<Long, EdgeInfo> edges = new HashMap<>();
        for (Precinct p : map.precincts()) {
            for (List<double[]> ring : p.rings()) {
                int n = ring.size();
                for (int i = 0; i < n; i++) {
                    double[] a = ring.get(i);
                    double[] b = ring.get((i + 1) % n);
                    long key = edgeKey(a, b);
                    EdgeInfo info = edges.get(key);
                    if (info == null) {
                        edges.put(key, new EdgeInfo(a, b, p.district()));
                    } else if (info.district != p.district()) {
                        info.differs = true;
                    }
                }
            }
        }
        List<double[]> boundary = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Map.Entry<Long, EdgeInfo> e : edges.entrySet()) {
            EdgeInfo info = e.getValue();
            if (!info.differs) continue;
            if (!seen.add(e.getKey())) continue;
            boundary.add(new double[] { info.ax, info.ay, info.bx, info.by });
        }
        return boundary;
    }

    private static final class EdgeInfo {
        final double ax, ay, bx, by;
        final int district;
        boolean differs;
        EdgeInfo(double[] a, double[] b, int district) {
            this.ax = a[0]; this.ay = a[1];
            this.bx = b[0]; this.by = b[1];
            this.district = district;
        }
    }

    private static long edgeKey(double[] a, double[] b) {
        long ka = vertexKey(a[0], a[1]);
        long kb = vertexKey(b[0], b[1]);
        // Order-independent.
        long lo = Math.min(ka, kb), hi = Math.max(ka, kb);
        return lo * 1315423911L ^ hi;
    }

    private static long vertexKey(double x, double y) {
        long ix = Math.round(x * 1e6);
        long iy = Math.round(y * 1e6);
        return (ix * 73856093L) ^ (iy * 19349663L);
    }

    private static Point2D.Double[] computeDistrictCentroids(RedistrictingMap map) {
        if (map == null) return null;
        int D = map.districtCount();
        double[] sumX = new double[D];
        double[] sumY = new double[D];
        long[] weight = new long[D];
        for (Precinct p : map.precincts()) {
            int d = p.district();
            if (d < 0 || d >= D) continue;
            double[] c = p.centroid();
            long w = Math.max(1, p.population());
            sumX[d] += c[0] * w;
            sumY[d] += c[1] * w;
            weight[d] += w;
        }
        Point2D.Double[] out = new Point2D.Double[D];
        for (int d = 0; d < D; d++) {
            if (weight[d] == 0) { out[d] = null; continue; }
            out[d] = new Point2D.Double(sumX[d] / weight[d], sumY[d] / weight[d]);
        }
        return out;
    }

    private static Path2D.Double polyPath(Precinct p) {
        Path2D.Double path = new Path2D.Double();
        for (List<double[]> ring : p.rings()) {
            for (int i = 0; i < ring.size(); i++) {
                double[] v = ring.get(i);
                if (i == 0) path.moveTo(v[0], v[1]);
                else path.lineTo(v[0], v[1]);
            }
            path.closePath();
        }
        return path;
    }

    private static double[] computeBbox(RedistrictingMap map) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Precinct p : map.precincts()) {
            for (List<double[]> ring : p.rings()) {
                for (double[] v : ring) {
                    if (v[0] < minX) minX = v[0];
                    if (v[1] < minY) minY = v[1];
                    if (v[0] > maxX) maxX = v[0];
                    if (v[1] > maxY) maxY = v[1];
                }
            }
        }
        return new double[] { minX, minY, maxX, maxY };
    }

    /** Perceptually distinct palette generated from the HSB wheel. */
    private static Color[] paletteFor(int n, boolean dark) {
        Color[] out = new Color[Math.max(1, n)];
        float sat = dark ? 0.65f : 0.55f;
        float bri = dark ? 0.85f : 0.92f;
        for (int i = 0; i < out.length; i++) {
            float h = (float) i / out.length;
            out[i] = Color.getHSBColor(h, sat, bri);
        }
        return out;
    }

    private static boolean pointInRing(double x, double y, List<double[]> ring) {
        boolean inside = false;
        int n = ring.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring.get(i)[0], yi = ring.get(i)[1];
            double xj = ring.get(j)[0], yj = ring.get(j)[1];
            boolean crosses = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi + 1e-18) + xi);
            if (crosses) inside = !inside;
        }
        return inside;
    }
}
