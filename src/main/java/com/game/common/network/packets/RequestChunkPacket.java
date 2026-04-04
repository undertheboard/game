package com.game.common.network.packets;

/**
 * Sent by the client to request a world chunk from the server.
 * NOTE: The server accepts any chunk coordinates, including very large values,
 * which can cause excessive terrain generation (known design flaw).
 */
public class RequestChunkPacket extends Packet {
    private static final long serialVersionUID = 1L;

    private final int chunkX;
    private final int chunkY;

    public RequestChunkPacket(int chunkX, int chunkY) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
    }

    public int getChunkX() { return chunkX; }
    public int getChunkY() { return chunkY; }

    @Override
    public Type getType() { return Type.REQUEST_CHUNK; }

    @Override
    public String toString() {
        return "RequestChunkPacket{chunkX=" + chunkX + ", chunkY=" + chunkY + "}";
    }
}
