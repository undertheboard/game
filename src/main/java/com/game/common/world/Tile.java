package com.game.common.world;

import java.io.Serializable;

/**
 * Represents a single tile in the world.
 */
public class Tile implements Serializable {
    private static final long serialVersionUID = 1L;

    private TileType type;
    private Biome biome;
    private float elevation;

    public Tile() {
        this.type = TileType.GRASS;
        this.biome = Biome.FOREST;
        this.elevation = 0f;
    }

    public Tile(TileType type, Biome biome, float elevation) {
        this.type = type;
        this.biome = biome;
        this.elevation = elevation;
    }

    public TileType getType() { return type; }
    public void setType(TileType type) { this.type = type; }

    public Biome getBiome() { return biome; }
    public void setBiome(Biome biome) { this.biome = biome; }

    public float getElevation() { return elevation; }
    public void setElevation(float elevation) { this.elevation = elevation; }

    @Override
    public String toString() {
        return "Tile{type=" + type + ", biome=" + biome + ", elevation=" + elevation + "}";
    }
}
