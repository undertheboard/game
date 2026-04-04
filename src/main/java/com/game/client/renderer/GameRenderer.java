package com.game.client.renderer;

import com.game.common.entity.Entity;
import com.game.common.world.Chunk;
import com.game.common.world.Tile;
import com.game.common.world.TileType;
import com.game.client.ui.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Primary Swing rendering panel.
 *
 * <p>Implements a 5-layer z-ordering system:
 * <ol>
 *   <li>{@link RenderLayer#WATER} – water and ocean tiles</li>
 *   <li>{@link RenderLayer#GROUND} – walkable ground tiles</li>
 *   <li>{@link RenderLayer#OBJECTS} – static world objects (trees, walls…)</li>
 *   <li>{@link RenderLayer#PLAYERS} – players and entities</li>
 *   <li>{@link RenderLayer#WEATHER_FX} – particles, lighting overlay</li>
 * </ol>
 *
 * <p>Only tiles within the camera's visible bounds are rendered to avoid
 * heap-space exhaustion when the world is very large.
 */
public class GameRenderer extends JPanel {
    private static final long serialVersionUID = 1L;

    // Tile colours per type
    private static final Map<TileType, Color> TILE_COLORS = new ConcurrentHashMap<>();
    static {
        TILE_COLORS.put(TileType.DEEP_WATER, new Color(0x003080));
        TILE_COLORS.put(TileType.WATER,      new Color(0x1E6BB8));
        TILE_COLORS.put(TileType.GRASS,      new Color(0x4CAF50));
        TILE_COLORS.put(TileType.FLOWER,     new Color(0x8BC34A));
        TILE_COLORS.put(TileType.TREE,       new Color(0x2E7D32));
        TILE_COLORS.put(TileType.SAND,       new Color(0xF5DEB3));
        TILE_COLORS.put(TileType.DRY_GRASS,  new Color(0xC8A96E));
        TILE_COLORS.put(TileType.CACTUS,     new Color(0x388E3C));
        TILE_COLORS.put(TileType.STONE,      new Color(0x9E9E9E));
        TILE_COLORS.put(TileType.RUIN_FLOOR, new Color(0xBCAAA4));
        TILE_COLORS.put(TileType.RUIN_WALL,  new Color(0x6D4C41));
        TILE_COLORS.put(TileType.RUIN_PILLAR,new Color(0x795548));
        TILE_COLORS.put(TileType.PATH,       new Color(0xD7CCC8));
        TILE_COLORS.put(TileType.LAVA,       new Color(0xFF5722));
    }

    private final Camera camera;
    private final LightingSystem lighting;
    private UITheme theme;

    private final Map<Long, Chunk> chunkCache = new ConcurrentHashMap<>();
    private final List<Entity> entities = new ArrayList<>();

    // Particle FX
    private final List<Particle> particles = new ArrayList<>();

    // HUD info
    private String hudText = "";

    public GameRenderer(Camera camera, LightingSystem lighting, UITheme theme) {
        this.camera = camera;
        this.lighting = lighting;
        this.theme = theme;
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void addChunk(Chunk chunk) {
        long key = packKey(chunk.getChunkX(), chunk.getChunkY());
        chunkCache.put(key, chunk);
    }

    public void addEntity(Entity entity) { entities.add(entity); }
    public void removeEntity(Entity entity) { entities.remove(entity); }

    public void setHudText(String text) { this.hudText = text; }
    public void setTheme(UITheme theme) { this.theme = theme; }

    public void addParticle(Particle particle) { particles.add(particle); }

    public void tickParticles() {
        particles.removeIf(p -> {
            p.tick();
            return !p.isAlive();
        });
    }

    // -------------------------------------------------------------------------
    // Swing paint
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Rectangle viewBounds = camera.getVisibleTileBounds();

        // Render layers in order
        paintLayer(g2, RenderLayer.WATER, viewBounds);
        paintLayer(g2, RenderLayer.GROUND, viewBounds);
        paintLayer(g2, RenderLayer.OBJECTS, viewBounds);
        paintLayer(g2, RenderLayer.PLAYERS, viewBounds);
        paintLayer(g2, RenderLayer.WEATHER_FX, viewBounds);

        paintHUD(g2);
    }

    private void paintLayer(Graphics2D g2, RenderLayer layer, Rectangle viewBounds) {
        switch (layer) {
            case WATER -> paintTileLayer(g2, viewBounds, true);
            case GROUND -> paintTileLayer(g2, viewBounds, false);
            case OBJECTS -> paintObjects(g2, viewBounds);
            case PLAYERS -> paintEntities(g2);
            case WEATHER_FX -> {
                paintParticles(g2);
                lighting.renderOverlay(g2, getWidth(), getHeight(), camera);
            }
        }
    }

    /**
     * Paints ground-level tiles that are within the camera viewport.
     * Only iterates visible chunks to avoid heap exhaustion.
     */
    private void paintTileLayer(Graphics2D g2, Rectangle viewBounds, boolean waterOnly) {
        int tileSize = (int) (Camera.TILE_SIZE * camera.getZoom());

        int startChunkX = Math.floorDiv(viewBounds.x, Chunk.CHUNK_SIZE);
        int startChunkY = Math.floorDiv(viewBounds.y, Chunk.CHUNK_SIZE);
        int endChunkX = Math.floorDiv(viewBounds.x + viewBounds.width, Chunk.CHUNK_SIZE) + 1;
        int endChunkY = Math.floorDiv(viewBounds.y + viewBounds.height, Chunk.CHUNK_SIZE) + 1;

        for (int cx = startChunkX; cx <= endChunkX; cx++) {
            for (int cy = startChunkY; cy <= endChunkY; cy++) {
                Chunk chunk = chunkCache.get(packKey(cx, cy));
                if (chunk == null) continue;

                for (int lx = 0; lx < Chunk.CHUNK_SIZE; lx++) {
                    for (int ly = 0; ly < Chunk.CHUNK_SIZE; ly++) {
                        int worldX = cx * Chunk.CHUNK_SIZE + lx;
                        int worldY = cy * Chunk.CHUNK_SIZE + ly;

                        // Viewport clipping
                        if (worldX < viewBounds.x || worldX > viewBounds.x + viewBounds.width) continue;
                        if (worldY < viewBounds.y || worldY > viewBounds.y + viewBounds.height) continue;

                        Tile tile = chunk.getTile(lx, ly);
                        boolean isWater = tile.getType() == TileType.WATER
                                || tile.getType() == TileType.DEEP_WATER;

                        if (waterOnly != isWater) continue;

                        int sx = camera.worldToScreenX(worldX);
                        int sy = camera.worldToScreenY(worldY);

                        Color color = TILE_COLORS.getOrDefault(tile.getType(), Color.MAGENTA);
                        g2.setColor(color);
                        g2.fillRect(sx, sy, tileSize, tileSize);

                        // Subtle grid line
                        g2.setColor(color.darker());
                        g2.drawRect(sx, sy, tileSize, tileSize);
                    }
                }
            }
        }
    }

    /** Renders non-walkable object tiles (trees, walls) with a symbol. */
    private void paintObjects(Graphics2D g2, Rectangle viewBounds) {
        int tileSize = (int) (Camera.TILE_SIZE * camera.getZoom());
        int fontSz = Math.max(8, tileSize - 4);
        g2.setFont(new Font("Monospaced", Font.BOLD, fontSz));

        int startChunkX = Math.floorDiv(viewBounds.x, Chunk.CHUNK_SIZE);
        int startChunkY = Math.floorDiv(viewBounds.y, Chunk.CHUNK_SIZE);
        int endChunkX = Math.floorDiv(viewBounds.x + viewBounds.width, Chunk.CHUNK_SIZE) + 1;
        int endChunkY = Math.floorDiv(viewBounds.y + viewBounds.height, Chunk.CHUNK_SIZE) + 1;

        for (int cx = startChunkX; cx <= endChunkX; cx++) {
            for (int cy = startChunkY; cy <= endChunkY; cy++) {
                Chunk chunk = chunkCache.get(packKey(cx, cy));
                if (chunk == null) continue;

                for (int lx = 0; lx < Chunk.CHUNK_SIZE; lx++) {
                    for (int ly = 0; ly < Chunk.CHUNK_SIZE; ly++) {
                        Tile tile = chunk.getTile(lx, ly);
                        if (tile.getType().isWalkable()) continue;

                        int worldX = cx * Chunk.CHUNK_SIZE + lx;
                        int worldY = cy * Chunk.CHUNK_SIZE + ly;
                        if (worldX < viewBounds.x || worldX > viewBounds.x + viewBounds.width) continue;
                        if (worldY < viewBounds.y || worldY > viewBounds.y + viewBounds.height) continue;

                        int sx = camera.worldToScreenX(worldX);
                        int sy = camera.worldToScreenY(worldY);

                        String symbol = tileSymbol(tile.getType());
                        g2.setColor(TILE_COLORS.getOrDefault(tile.getType(), Color.WHITE));
                        g2.drawString(symbol, sx + 2, sy + tileSize - 2);
                    }
                }
            }
        }
    }

    private void paintEntities(Graphics2D g2) {
        int tileSize = (int) (Camera.TILE_SIZE * camera.getZoom());
        for (Entity entity : entities) {
            int sx = camera.worldToScreenX(entity.getX());
            int sy = camera.worldToScreenY(entity.getY());

            // Entity body
            g2.setColor(theme.playerColor());
            g2.fillOval(sx + 2, sy + 2, tileSize - 4, tileSize - 4);
            g2.setColor(theme.playerColor().darker());
            g2.drawOval(sx + 2, sy + 2, tileSize - 4, tileSize - 4);

            // Name label
            g2.setColor(theme.textColor());
            g2.setFont(new Font(theme.fontFamily(), Font.PLAIN, 10));
            g2.drawString(entity.getName(), sx, sy - 2);

            // HP bar
            double hpRatio = entity.getHp()
                    / entity.getStats().getFinalValue(com.game.common.stats.StatType.MAX_HP);
            g2.setColor(Color.RED);
            g2.fillRect(sx, sy + tileSize + 1, tileSize, 4);
            g2.setColor(Color.GREEN);
            g2.fillRect(sx, sy + tileSize + 1, (int) (tileSize * hpRatio), 4);
        }
    }

    private void paintParticles(Graphics2D g2) {
        for (Particle p : particles) {
            int sx = camera.worldToScreenX(p.x);
            int sy = camera.worldToScreenY(p.y);
            g2.setColor(new Color(p.r, p.g, p.b, p.alpha));
            g2.fillOval(sx - p.size / 2, sy - p.size / 2, p.size, p.size);
        }
    }

    private void paintHUD(Graphics2D g2) {
        if (hudText == null || hudText.isEmpty()) return;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(8, 8, 220, 60, 10, 10);
        g2.setColor(theme.textColor());
        g2.setFont(new Font(theme.fontFamily(), Font.BOLD, 12));
        int y = 26;
        for (String line : hudText.split("\n")) {
            g2.drawString(line, 14, y);
            y += 16;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static long packKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static String tileSymbol(TileType type) {
        return switch (type) {
            case TREE -> "🌲";
            case CACTUS -> "🌵";
            case RUIN_WALL -> "█";
            case RUIN_PILLAR -> "▐";
            default -> "?";
        };
    }

    // -------------------------------------------------------------------------
    // Particle inner class
    // -------------------------------------------------------------------------

    /** A simple particle for weather/FX effects. */
    public static class Particle {
        public double x, y;
        public double vx, vy;
        public int r, g, b;
        public int alpha;
        public int size;
        private int life;
        private int maxLife;

        public Particle(double x, double y, double vx, double vy,
                        int r, int g, int b, int size, int life) {
            this.x = x; this.y = y;
            this.vx = vx; this.vy = vy;
            this.r = r; this.g = g; this.b = b;
            this.size = size;
            this.life = life;
            this.maxLife = life;
            this.alpha = 255;
        }

        public void tick() {
            x += vx;
            y += vy;
            life--;
            alpha = (int) (255 * ((double) life / maxLife));
        }

        public boolean isAlive() { return life > 0; }
    }
}
