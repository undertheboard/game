package com.game.common.network.packets;

import com.game.common.world.Chunk;

/**
 * Sent by the server to deliver chunk data to the client.
 */
public class ChunkDataPacket extends Packet {
    private static final long serialVersionUID = 1L;

    private final Chunk chunk;

    public ChunkDataPacket(Chunk chunk) {
        this.chunk = chunk;
    }

    public Chunk getChunk() { return chunk; }

    @Override
    public Type getType() { return Type.CHUNK_DATA; }

    @Override
    public String toString() {
        return "ChunkDataPacket{chunk=" + chunk + "}";
    }
}
