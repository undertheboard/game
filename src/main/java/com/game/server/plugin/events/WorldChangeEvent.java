package com.game.server.plugin.events;

import com.game.common.world.TileType;

/**
 * Fired when the world map is modified (tile placement).
 */
public class WorldChangeEvent extends GameEvent {
    private final int worldX;
    private final int worldY;
    private final TileType newTileType;
    private final String modifierId;

    public WorldChangeEvent(int worldX, int worldY, TileType newTileType, String modifierId) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.newTileType = newTileType;
        this.modifierId = modifierId;
    }

    public int getWorldX() { return worldX; }
    public int getWorldY() { return worldY; }
    public TileType getNewTileType() { return newTileType; }
    public String getModifierId() { return modifierId; }

    @Override
    public String getEventName() { return "WorldChangeEvent"; }
}
