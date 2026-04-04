package com.game.client.renderer;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulates dynamic lighting using radial gradient overlays.
 *
 * <p>A dark ambient overlay is rendered over the world and then "punched out"
 * by circular light sources (torches, player aura, etc.) using
 * {@link AlphaComposite#DST_OUT}.  A day/night cycle modulates the ambient
 * darkness level over time.
 */
public class LightingSystem {
    /** Full day-night cycle in game ticks (20 TPS × 60 s × 20 min). */
    public static final long DAY_LENGTH_TICKS = 20 * 60 * 20;

    /** Minimum darkness alpha at noon (0 = fully transparent / daylight). */
    private static final float MIN_DARKNESS = 0.0f;
    /** Maximum darkness alpha at midnight. */
    private static final float MAX_DARKNESS = 0.85f;

    private final List<LightSource> lights = new ArrayList<>();
    private long currentTick = 0;

    /** Advances the lighting simulation by one tick. */
    public void tick() {
        currentTick++;
    }

    public void addLight(LightSource source) { lights.add(source); }
    public void removeLight(LightSource source) { lights.remove(source); }
    public void clearLights() { lights.clear(); }

    /**
     * Returns the ambient darkness alpha for the current tick.
     * Uses a cosine curve so the transition is smooth.
     * 0.0 = full daylight, 1.0 = pitch black.
     */
    public float getAmbientDarkness() {
        double phase = (double) (currentTick % DAY_LENGTH_TICKS) / DAY_LENGTH_TICKS;
        // cos(0) = 1 → noon, cos(π) = -1 → midnight
        double cosPhase = Math.cos(phase * 2 * Math.PI);
        // Map [-1,1] → [MAX,MIN]
        return (float) (MIN_DARKNESS + (MAX_DARKNESS - MIN_DARKNESS) * (1.0 - cosPhase) / 2.0);
    }

    /**
     * Renders the lighting overlay onto the supplied graphics context.
     *
     * @param g          graphics context of the render panel
     * @param width      viewport width in pixels
     * @param height     viewport height in pixels
     * @param camera     current camera (for world→screen coordinate mapping)
     */
    public void renderOverlay(Graphics2D g, int width, int height, Camera camera) {
        float darkness = getAmbientDarkness();
        if (darkness <= 0.01f) return; // Fully lit – skip overlay

        // 1. Create an offscreen image for the light mask
        BufferedImage lightMask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D lm = lightMask.createGraphics();
        lm.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fill mask with ambient darkness
        lm.setColor(new Color(0f, 0f, 0f, darkness));
        lm.fillRect(0, 0, width, height);

        // 2. For each light source, punch a radial gradient hole
        lm.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT));
        for (LightSource light : lights) {
            int sx = camera.worldToScreenX(light.worldX);
            int sy = camera.worldToScreenY(light.worldY);
            int radius = (int) (light.radius * camera.TILE_SIZE * camera.getZoom());

            RadialGradientPaint radial = new RadialGradientPaint(
                    new Point2D.Float(sx, sy),
                    radius,
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{
                            new Color(1f, 1f, 1f, light.intensity),
                            new Color(1f, 1f, 1f, light.intensity * 0.4f),
                            new Color(0f, 0f, 0f, 0f)
                    });
            lm.setPaint(radial);
            lm.fillOval(sx - radius, sy - radius, radius * 2, radius * 2);
        }
        lm.dispose();

        // 3. Composite the mask onto the scene
        g.drawImage(lightMask, 0, 0, null);
    }

    /** Returns the time of day as a human-readable string. */
    public String getTimeOfDay() {
        long ticks = currentTick % DAY_LENGTH_TICKS;
        int totalMinutes = (int) (ticks * 24 * 60 / DAY_LENGTH_TICKS);
        int hour = totalMinutes / 60;
        int minute = totalMinutes % 60;
        return String.format("%02d:%02d", hour, minute);
    }

    public long getCurrentTick() { return currentTick; }
    public void setCurrentTick(long tick) { this.currentTick = tick; }

    // -------------------------------------------------------------------------
    // LightSource descriptor
    // -------------------------------------------------------------------------

    /** Describes a point light in world-tile coordinates. */
    public static class LightSource {
        public double worldX;
        public double worldY;
        /** Radius in tiles. */
        public float radius;
        /** Peak alpha of the light (0 = dark, 1 = fully illuminated). */
        public float intensity;

        public LightSource(double worldX, double worldY, float radius, float intensity) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.radius = radius;
            this.intensity = intensity;
        }
    }
}
