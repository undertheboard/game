package com.game.client.renderer;

/**
 * The five Z-ordered rendering layers used by {@link GameRenderer}.
 * Layers are painted in enum-declaration order (lowest index = bottom).
 */
public enum RenderLayer {
    /** Bodies of water, ocean floor tiles. */
    WATER,
    /** Ground tiles: grass, sand, ruins floor, stone. */
    GROUND,
    /** Static world objects: trees, cacti, walls, pillars. */
    OBJECTS,
    /** Players and moving entities. */
    PLAYERS,
    /** Weather effects, particle FX, and the lighting overlay. */
    WEATHER_FX
}
