package com.game.common.world;

/**
 * Tile type definitions for the world map.
 */
public enum TileType {
    // Forest biome tiles
    GRASS(true),
    TREE(false),
    FLOWER(true),

    // Desert biome tiles
    SAND(true),
    CACTUS(false),
    DRY_GRASS(true),

    // Ruins biome tiles
    RUIN_FLOOR(true),
    RUIN_WALL(false),
    RUIN_PILLAR(false),

    // Universal tiles
    WATER(false),
    DEEP_WATER(false),
    STONE(true),
    LAVA(false),
    PATH(true);

    private final boolean walkable;

    TileType(boolean walkable) {
        this.walkable = walkable;
    }

    public boolean isWalkable() {
        return walkable;
    }
}
