package com.game.common.network.packets;

import com.game.common.world.TileType;

/**
 * Sent by the client to place a tile in the world (World Editor / God Mode tool).
 * Since the server is client-authoritative, any tile can be placed anywhere.
 */
public class PlaceTilePacket extends Packet {
    private static final long serialVersionUID = 1L;

    private final int worldX;
    private final int worldY;
    private final TileType tileType;

    public PlaceTilePacket(int worldX, int worldY, TileType tileType) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.tileType = tileType;
    }

    public int getWorldX() { return worldX; }
    public int getWorldY() { return worldY; }
    public TileType getTileType() { return tileType; }

    @Override
    public Type getType() { return Type.PLACE_TILE; }

    @Override
    public String toString() {
        return "PlaceTilePacket{worldX=" + worldX + ", worldY=" + worldY + ", tileType=" + tileType + "}";
    }
}
