package com.redistricting.gui;

import com.redistricting.model.District;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pannable / zoomable Swing canvas that draws and (optionally) edits a
 * {@link RedistrictingMap}.
 *
 * <p>Four colouring modes are exposed:
 * <ul>
 *   <li>{@link ViewMode#DISTRICT} — colour each precinct by its district id
 *       using a perceptually-distinct palette.</li>
 *   <li>{@link ViewMode#DISTRICT_WHOLE} — same palette but precinct outlines
 *       are dropped so each district reads as a single solid block.</li>
 *   <li>{@link ViewMode#PARTISAN_LEAN} — shade each precinct from deep red
 *       (Republican) through near-white (50/50) to deep blue (Democratic)
 *       using a high-resolution multi-stop diverging gradient with a mild
 *       sigmoid spread, so the map shows far more shades than a simple
 *       three-stop blend.</li>
 *   <li>{@link ViewMode#PARTISAN_LEAN_DISTRICT} — colour every precinct in a
 *       district with the district-aggregate lean.</li>
 * </ul>
 *
 * <p>When {@link #setEditMode(boolean) edit mode} is on, click / drag paints
 * the precincts under the cursor (within {@link #setBrushSize(int) brush
 * radius pixels}) into the {@link #setActiveDistrict(int) active district}.
 * Edits are recorded onto an undo stack — {@link #undo()} / {@link #redo()}
 * roll a whole stroke back or forward.
 *
 * <p>Tooltips expose precinct details. Drag (when not editing) to pan,
 * scroll wheel to zoom about the cursor.
 */
public final class MapPanel extends JPanel {

    /** Colouring scheme for the map fill. */
    public enum ViewMode {
        DISTRICT, DISTRICT_WHOLE, PARTISAN_LEAN, PARTISAN_LEAN_DISTRICT
    }

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

    // ---- editing state ---------------------------------------------------
    private boolean editMode = false;
    private int activeDistrict = 0;
    /** Brush radius in screen pixels (0 = exactly one precinct under the cursor). */
    private int brushSize = 0;
    private int cursorX = -1, cursorY = -1;
    private boolean cursorInside = false;

    /** Undo/redo stacks of completed strokes. Each stroke is a precinctId → previousDistrict map. */
    private final Deque<Map<String, Integer>> undoStack = new ArrayDeque<>();
    private final Deque<Map<String, Integer>> redoStack = new ArrayDeque<>();
    /** The stroke currently in progress (mouse pressed → released). */
    private Map<String, Integer> currentStroke;

    /** Listener fired when the precinct → district assignment changes. */
    private Consumer<RedistrictingMap> changeListener;

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
                if (editMode && SwingUtilities.isLeftMouseButton(e)) {
                    currentStroke = new HashMap<>();
                    paintAt(e.getX(), e.getY());
                }
            }
            @Override public void mouseDragged(MouseEvent e) {
                cursorX = e.getX(); cursorY = e.getY(); cursorInside = true;
                if (editMode && SwingUtilities.isLeftMouseButton(e)) {
                    paintAt(e.getX(), e.getY());
                    repaint();
                    return;
                }
                offsetX += e.getX() - lastDragX;
                offsetY += e.getY() - lastDragY;
                lastDragX = e.getX();
                lastDragY = e.getY();
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (editMode && currentStroke != null) {
                    if (!currentStroke.isEmpty()) {
                        undoStack.push(currentStroke);
                        redoStack.clear();
                        fireChange();
                    }
                    currentStroke = null;
                }
            }
            @Override public void mouseMoved(MouseEvent e) {
                cursorX = e.getX(); cursorY = e.getY(); cursorInside = true;
                if (editMode) repaint();
            }
            @Override public void mouseEntered(MouseEvent e) { cursorInside = true; }
            @Override public void mouseExited(MouseEvent e) {
                cursorInside = false;
                if (editMode) repaint();
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
        rebuildDerived();
        undoStack.clear();
        redoStack.clear();
        if (activeDistrict >= map.districtCount()) activeDistrict = 0;
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

    // ---- editing API -----------------------------------------------------

    public boolean isEditMode() { return editMode; }
    public void setEditMode(boolean v) {
        if (v == editMode) return;
        editMode = v;
        setCursor(Cursor.getPredefinedCursor(v ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }

    public int getActiveDistrict() { return activeDistrict; }
    public void setActiveDistrict(int d) {
        if (map == null) { activeDistrict = Math.max(0, d); return; }
        activeDistrict = Math.max(0, Math.min(map.districtCount() - 1, d));
        repaint();
    }

    public int getBrushSize() { return brushSize; }
    public void setBrushSize(int px) {
        brushSize = Math.max(0, px);
        if (editMode) repaint();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void undo() { swap(undoStack, redoStack); }
    public void redo() { swap(redoStack, undoStack); }

    private void swap(Deque<Map<String, Integer>> from, Deque<Map<String, Integer>> to) {
        if (map == null || from.isEmpty()) return;
        Map<String, Integer> stroke = from.pop();
        Map<String, Integer> reverse = new HashMap<>();
        Map<String, Precinct> byId = new HashMap<>();
        for (Precinct p : map.precincts()) byId.put(p.id(), p);
        for (Map.Entry<String, Integer> e : stroke.entrySet()) {
            Precinct p = byId.get(e.getKey());
            if (p == null) continue;
            reverse.put(p.id(), p.district());
            p.setDistrict(e.getValue());
        }
        to.push(reverse);
        rebuildDerived();
        repaint();
        fireChange();
    }

    /** Listener fired whenever a paint stroke (or undo/redo) changes assignments. */
    public void setChangeListener(Consumer<RedistrictingMap> l) { this.changeListener = l; }

    private void fireChange() {
        if (changeListener != null && map != null) changeListener.accept(map);
    }

    /**
     * Paint at screen pixel (sx, sy): every precinct whose centroid (or any
     * vertex within brush radius) falls under the brush is reassigned to the
     * active district. Records the previous assignment in {@code currentStroke}
     * so the move can be undone as a single unit.
     */
    private void paintAt(int sx, int sy) {
        if (map == null || currentStroke == null) return;
        // World-space brush radius.
        double r = brushSize / Math.max(1e-9, scale);
        double mx = (sx - offsetX) / scale;
        double my = -(sy - offsetY) / scale;
        boolean changed = false;
        for (Precinct p : map.precincts()) {
            if (p.district() == activeDistrict) continue;
            if (!precinctTouches(p, mx, my, r)) continue;
            currentStroke.putIfAbsent(p.id(), p.district());
            p.setDistrict(activeDistrict);
            changed = true;
        }
        if (changed) rebuildDerived();
    }

    private static boolean precinctTouches(Precinct p, double mx, double my, double r) {
        // Fast path: brush radius 0 → require point-in-polygon.
        if (r <= 0) {
            for (List<double[]> ring : p.rings()) {
                if (pointInRing(mx, my, ring)) return true;
            }
            return false;
        }
        double r2 = r * r;
        for (List<double[]> ring : p.rings()) {
            // Quick: any vertex within radius?
            for (double[] v : ring) {
                double dx = v[0] - mx, dy = v[1] - my;
                if (dx * dx + dy * dy <= r2) return true;
            }
            // Or cursor inside the ring at all (e.g. brush smaller than precinct).
            if (pointInRing(mx, my, ring)) return true;
        }
        return false;
    }

    private void rebuildDerived() {
        this.districtBoundary = computeDistrictBoundary(map);
        this.districtCentroid = computeDistrictCentroids(map);
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

        boolean wholeDistrict = (viewMode == ViewMode.DISTRICT_WHOLE
                || viewMode == ViewMode.PARTISAN_LEAN_DISTRICT);

        // Fill precincts.
        for (Precinct p : map.precincts()) {
            Color fill;
            switch (viewMode) {
                case PARTISAN_LEAN ->
                    fill = leanColor(precinctLean(p, districtLean), dark);
                case PARTISAN_LEAN_DISTRICT ->
                    fill = leanColor(safeLean(districtLean, p.district()), dark);
                case DISTRICT_WHOLE, DISTRICT ->
                    fill = palette[Math.floorMod(p.district(), palette.length)];
                default -> fill = palette[0];
            }
            g2.setColor(fill);
            Path2D.Double path = polyPath(p);
            g2.fill(path.createTransformedShape(t));
        }

        // Optional thin precinct outlines (suppressed in whole-district modes
        // unless the user has explicitly turned them back on).
        if (showPrecinctLines && !wholeDistrict) {
            g2.setStroke(new BasicStroke(0.5f));
            g2.setColor(dark ? new Color(255, 255, 255, 60) : new Color(0, 0, 0, 80));
            for (Precinct p : map.precincts()) {
                Path2D.Double path = polyPath(p);
                g2.draw(path.createTransformedShape(t));
            }
        }

        // District boundaries — thicker, contrasting; toggleable from the toolbar.
        if (showDistrictLines) {
            g2.setStroke(new BasicStroke(wholeDistrict ? 2.4f : 1.8f));
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
        drawBrushOverlay(g2, palette);
        g2.dispose();
    }

    private void drawBrushOverlay(Graphics2D g2, Color[] palette) {
        if (!editMode || !cursorInside || map == null) return;
        Color ring = palette[Math.floorMod(activeDistrict, palette.length)];
        g2.setStroke(new BasicStroke(2f));
        // Crosshair / brush radius.
        int r = Math.max(brushSize, 6);
        g2.setColor(new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 200));
        g2.drawOval(cursorX - r, cursorY - r, r * 2, r * 2);
        if (brushSize > 0) {
            g2.setColor(new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 60));
            g2.fillOval(cursorX - brushSize, cursorY - brushSize,
                    brushSize * 2, brushSize * 2);
        }
        // "D#" badge.
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        drawOutlinedString(g2, "D" + (activeDistrict + 1),
                cursorX + r + 4, cursorY - r - 2, dark);
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
        Color box = dark ? new Color(24, 24, 30, 235) : new Color(255, 255, 255, 230);
        Color text = dark ? DarkTheme.FOREGROUND : Color.DARK_GRAY;
        Color line = dark ? DarkTheme.BORDER : Color.BLACK;

        int rows = map.districtCount();
        g2.setColor(box);
        g2.fillRoundRect(x - 6, y - 6, 240, rows * (sh + pad) + 32, 12, 12);
        g2.setColor(dark ? DarkTheme.ACCENT : new Color(0x1F55CC));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x - 6, y - 6, 240, rows * (sh + pad) + 32, 12, 12);
        g2.setColor(text);
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g2.drawString(map.name(), x, y + 12);
        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        y += 22;
        for (District d : map.districts()) {
            boolean leanView = (viewMode == ViewMode.PARTISAN_LEAN
                    || viewMode == ViewMode.PARTISAN_LEAN_DISTRICT);
            Color swatch = leanView
                    ? leanColor(safeLean(districtLean, d.id()), dark)
                    : palette[Math.floorMod(d.id(), palette.length)];
            g2.setColor(swatch);
            g2.fillRoundRect(x, y, sw, sh, 4, 4);
            g2.setColor(line);
            g2.drawRoundRect(x, y, sw, sh, 4, 4);
            // Highlight the active district when editing.
            if (editMode && d.id() == activeDistrict) {
                g2.setColor(dark ? DarkTheme.ACCENT : new Color(0x1F55CC));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(x - 2, y - 2, sw + 4, sh + 4, 6, 6);
            }
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
        return safeLean(districtLean, p.district());
    }

    private static double safeLean(double[] districtLean, int idx) {
        if (districtLean.length == 0) return 0.5;
        double dl = districtLean[Math.max(0, Math.min(districtLean.length - 1, idx))];
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

    /**
     * Map a Dem-share in [0,1] to a high-resolution diverging gradient
     * inspired by ColorBrewer's <i>RdBu</i> ramp.
     *
     * <p>Compared with a plain three-stop red→grey→blue blend this
     * interpolates over <em>nine</em> anchor colours and applies a mild
     * sigmoid spread, so adjacent precincts at e.g. 47% and 49% Dem are
     * visibly distinct rather than collapsing onto the same near-grey hue.
     * The result is many more shades of red and blue across the map.
     */
    static Color leanColor(double share, boolean dark) {
        if (Double.isNaN(share)) return dark ? new Color(0x40, 0x40, 0x48)
                                             : new Color(0xC8, 0xC8, 0xC8);
        share = Math.max(0, Math.min(1, share));
        // Mild sigmoid-style spread around 0.5 so the centre region uses
        // more of the gradient instead of crushing everything to grey.
        double centred = (share - 0.5) * 2.0; // -1..+1
        double spread = Math.tanh(centred * 1.6) / Math.tanh(1.6); // -1..+1
        double t = (spread + 1.0) * 0.5; // 0..1, monotone, smooth

        Color[] stops = dark ? DARK_RDBU : LIGHT_RDBU;
        double scaled = t * (stops.length - 1);
        int idx = (int) Math.floor(scaled);
        if (idx >= stops.length - 1) return stops[stops.length - 1];
        if (idx < 0) return stops[0];
        return blend(stops[idx], stops[idx + 1], scaled - idx);
    }

    /** 9-stop diverging palette (deep red → near-white → deep blue). */
    private static final Color[] LIGHT_RDBU = {
            new Color(0x67, 0x00, 0x1F),
            new Color(0xB2, 0x18, 0x2B),
            new Color(0xD6, 0x60, 0x4D),
            new Color(0xF4, 0xA5, 0x82),
            new Color(0xF7, 0xF7, 0xF7),
            new Color(0x92, 0xC5, 0xDE),
            new Color(0x43, 0x93, 0xC3),
            new Color(0x21, 0x66, 0xAC),
            new Color(0x05, 0x30, 0x61),
    };

    /** 9-stop diverging palette tuned for the dark theme (mid-tone is muted grey). */
    private static final Color[] DARK_RDBU = {
            new Color(0x80, 0x10, 0x20),
            new Color(0xC0, 0x30, 0x40),
            new Color(0xE0, 0x60, 0x60),
            new Color(0xE8, 0x9A, 0x82),
            new Color(0x80, 0x80, 0x88),
            new Color(0x82, 0xB0, 0xD8),
            new Color(0x4F, 0x88, 0xC8),
            new Color(0x2A, 0x60, 0xB0),
            new Color(0x10, 0x38, 0x80),
    };

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
