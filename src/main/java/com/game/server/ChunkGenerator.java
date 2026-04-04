package com.game.server;

import com.game.common.world.Biome;
import com.game.common.world.Chunk;
import com.game.common.world.Tile;
import com.game.common.world.TileType;
import com.game.server.noise.OpenSimplexNoise;

/**
 * Procedurally generates chunk terrain using OpenSimplex noise.
 * Two noise maps are combined:
 *  - Elevation noise determines land/water and terrain roughness.
 *  - Biome noise determines which biome type applies to land tiles.
 */
public class ChunkGenerator {
    private static final double ELEVATION_SCALE = 0.003;
    private static final double BIOME_SCALE = 0.001;
    private static final double DETAIL_SCALE = 0.02;

    private static final double WATER_THRESHOLD = -0.15;
    private static final double DEEP_WATER_THRESHOLD = -0.40;

    private static final double BIOME_FOREST_MAX = 0.33;
    private static final double BIOME_DESERT_MAX = 0.66;

    // Tree/cactus/ruin feature thresholds
    private static final double FEATURE_THRESHOLD = 0.55;

    private final OpenSimplexNoise elevationNoise;
    private final OpenSimplexNoise biomeNoise;
    private final OpenSimplexNoise detailNoise;

    public ChunkGenerator(long worldSeed) {
        this.elevationNoise = new OpenSimplexNoise(worldSeed);
        this.biomeNoise = new OpenSimplexNoise(worldSeed ^ 0xDEADBEEFCAFEL);
        this.detailNoise = new OpenSimplexNoise(worldSeed ^ 0xABCDEF012345L);
    }

    /**
     * Generates all tiles for the given chunk using procedural noise.
     */
    public Chunk generate(int chunkX, int chunkY) {
        Chunk chunk = new Chunk(chunkX, chunkY);

        for (int lx = 0; lx < Chunk.CHUNK_SIZE; lx++) {
            for (int ly = 0; ly < Chunk.CHUNK_SIZE; ly++) {
                int worldX = chunkX * Chunk.CHUNK_SIZE + lx;
                int worldY = chunkY * Chunk.CHUNK_SIZE + ly;

                double elevation = elevationNoise.noise2Octaves(
                        worldX * ELEVATION_SCALE, worldY * ELEVATION_SCALE, 6, 2.0, 0.5);
                double biomeVal = biomeNoise.noise2(
                        worldX * BIOME_SCALE, worldY * BIOME_SCALE);
                double detail = detailNoise.noise2(
                        worldX * DETAIL_SCALE, worldY * DETAIL_SCALE);

                Tile tile = buildTile(elevation, biomeVal, detail);
                chunk.setTile(lx, ly, tile);
            }
        }
        return chunk;
    }

    private Tile buildTile(double elevation, double biomeVal, double detail) {
        // Water tiles
        if (elevation < DEEP_WATER_THRESHOLD) {
            return new Tile(TileType.DEEP_WATER, Biome.OCEAN, (float) elevation);
        }
        if (elevation < WATER_THRESHOLD) {
            return new Tile(TileType.WATER, Biome.OCEAN, (float) elevation);
        }

        // Normalise biomeVal from [-1,1] to [0,1]
        double normalBiome = (biomeVal + 1.0) / 2.0;

        if (normalBiome < BIOME_FOREST_MAX) {
            return buildForestTile(elevation, detail);
        } else if (normalBiome < BIOME_DESERT_MAX) {
            return buildDesertTile(elevation, detail);
        } else {
            return buildRuinsTile(elevation, detail);
        }
    }

    private Tile buildForestTile(double elevation, double detail) {
        TileType type;
        if (detail > FEATURE_THRESHOLD) {
            type = TileType.TREE;
        } else if (detail > 0.2 && detail < 0.3) {
            type = TileType.FLOWER;
        } else if (elevation > 0.5) {
            type = TileType.STONE;
        } else {
            type = TileType.GRASS;
        }
        return new Tile(type, Biome.FOREST, (float) elevation);
    }

    private Tile buildDesertTile(double elevation, double detail) {
        TileType type;
        if (detail > FEATURE_THRESHOLD) {
            type = TileType.CACTUS;
        } else if (elevation > 0.55) {
            type = TileType.STONE;
        } else if (detail < -0.3) {
            type = TileType.DRY_GRASS;
        } else {
            type = TileType.SAND;
        }
        return new Tile(type, Biome.DESERT, (float) elevation);
    }

    private Tile buildRuinsTile(double elevation, double detail) {
        TileType type;
        if (detail > FEATURE_THRESHOLD) {
            type = TileType.RUIN_WALL;
        } else if (detail > 0.3) {
            type = TileType.RUIN_PILLAR;
        } else {
            type = TileType.RUIN_FLOOR;
        }
        return new Tile(type, Biome.RUINS, (float) elevation);
    }
}
