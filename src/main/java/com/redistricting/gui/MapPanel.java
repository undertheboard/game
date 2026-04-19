package com.redistricting.gui;

import com.redistricting.model.District;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * A pannable / zoomable Swing canvas that draws a {@link RedistrictingMap}
 * with each district in a distinct colour. Tooltips expose precinct details.
 */
public final class MapPanel extends JPanel {

    private RedistrictingMap map;
    private double scale = 1.0;
    private double offsetX = 0, offsetY = 0;
    private int lastDragX, lastDragY;
    private double[] bbox; // minX, minY, maxX, maxY in map units

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
                // Zoom about the cursor.
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

    public void setMap(RedistrictingMap map) {
        this.map = map;
        this.bbox = computeBbox(map);
        resetView();
        repaint();
    }

    public RedistrictingMap getMap() { return map; }

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
        // Centre the map. Note Y is flipped (geographic up vs. screen down).
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
                    return String.format(
                            "<html><b>%s</b><br/>District: %d<br/>"
                            + "Pop: %,d<br/>Dem: %,d &nbsp;Rep: %,d</html>",
                            p.id(), p.district() + 1, p.population(),
                            p.demVotes(), p.repVotes());
                }
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (map == null) {
            g.setColor(Color.DARK_GRAY);
            g.drawString("No plan loaded. Use File → Import from Dave's Redistricting…",
                    20, 30);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform t = new AffineTransform();
        t.translate(offsetX, offsetY);
        t.scale(scale, -scale);

        Color[] palette = paletteFor(map.districtCount());

        // Fill precincts.
        for (Precinct p : map.precincts()) {
            Path2D.Double path = polyPath(p);
            g2.setColor(palette[p.district() % palette.length]);
            Path2D.Double xformed = (Path2D.Double) path.createTransformedShape(t);
            g2.fill(xformed);
        }
        // Stroke precincts (thin) then districts (thick).
        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(new Color(0, 0, 0, 80));
        for (Precinct p : map.precincts()) {
            Path2D.Double path = polyPath(p);
            g2.draw(path.createTransformedShape(t));
        }

        drawLegend(g2, palette);
        g2.dispose();
    }

    private void drawLegend(Graphics2D g2, Color[] palette) {
        if (map == null) return;
        int x = 12, y = 12, sw = 18, sh = 14, pad = 4;
        g2.setColor(new Color(255, 255, 255, 220));
        int rows = map.districtCount();
        g2.fillRoundRect(x - 4, y - 4, 170, rows * (sh + pad) + 24, 8, 8);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString(map.name(), x, y + 12);
        y += 18;
        for (District d : map.districts()) {
            g2.setColor(palette[d.id() % palette.length]);
            g2.fillRect(x, y, sw, sh);
            g2.setColor(Color.BLACK);
            g2.drawRect(x, y, sw, sh);
            g2.drawString(String.format("D%d  pop %,d", d.id() + 1, d.totalPopulation()),
                    x + sw + 6, y + sh - 2);
            y += sh + pad;
        }
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

    /** A perceptually distinct palette generated from the HSB wheel. */
    private static Color[] paletteFor(int n) {
        Color[] out = new Color[Math.max(1, n)];
        for (int i = 0; i < out.length; i++) {
            float h = (float) i / out.length;
            out[i] = Color.getHSBColor(h, 0.55f, 0.92f);
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
