package com.game.client.renderer;

import java.awt.Rectangle;

/**
 * Tracks the viewport position and zoom level.
 * All world-to-screen coordinate transformations go through this class.
 */
public class Camera {
    public static final int TILE_SIZE = 32;

    private double worldX;
    private double worldY;
    private double zoom;
    private int viewportWidth;
    private int viewportHeight;

    public Camera(int viewportWidth, int viewportHeight) {
        this.worldX = 0;
        this.worldY = 0;
        this.zoom = 1.0;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    /** Moves the camera so it is centered on the given world coordinate. */
    public void centerOn(double worldX, double worldY) {
        this.worldX = worldX - (viewportWidth / 2.0 / (TILE_SIZE * zoom));
        this.worldY = worldY - (viewportHeight / 2.0 / (TILE_SIZE * zoom));
    }

    /** Converts a world-tile X coordinate to screen pixels. */
    public int worldToScreenX(double tileX) {
        return (int) ((tileX - worldX) * TILE_SIZE * zoom);
    }

    /** Converts a world-tile Y coordinate to screen pixels. */
    public int worldToScreenY(double tileY) {
        return (int) ((tileY - worldY) * TILE_SIZE * zoom);
    }

    /** Converts a screen pixel X to world-tile coordinate. */
    public double screenToWorldX(int screenX) {
        return screenX / (TILE_SIZE * zoom) + worldX;
    }

    /** Converts a screen pixel Y to world-tile coordinate. */
    public double screenToWorldY(int screenY) {
        return screenY / (TILE_SIZE * zoom) + worldY;
    }

    /**
     * Returns the rectangle of visible world-tile coordinates as a
     * {@link Rectangle} with (x, y) = first visible tile, width/height = tile count.
     */
    public Rectangle getVisibleTileBounds() {
        int tilesX = (int) Math.ceil(viewportWidth / (TILE_SIZE * zoom)) + 2;
        int tilesY = (int) Math.ceil(viewportHeight / (TILE_SIZE * zoom)) + 2;
        int startX = (int) Math.floor(worldX) - 1;
        int startY = (int) Math.floor(worldY) - 1;
        return new Rectangle(startX, startY, tilesX, tilesY);
    }

    public double getWorldX() { return worldX; }
    public void setWorldX(double worldX) { this.worldX = worldX; }

    public double getWorldY() { return worldY; }
    public void setWorldY(double worldY) { this.worldY = worldY; }

    public double getZoom() { return zoom; }
    public void setZoom(double zoom) { this.zoom = Math.max(0.25, Math.min(4.0, zoom)); }

    public int getViewportWidth() { return viewportWidth; }
    public void setViewportWidth(int viewportWidth) { this.viewportWidth = viewportWidth; }

    public int getViewportHeight() { return viewportHeight; }
    public void setViewportHeight(int viewportHeight) { this.viewportHeight = viewportHeight; }

    public void pan(double dx, double dy) {
        this.worldX += dx;
        this.worldY += dy;
    }
}
