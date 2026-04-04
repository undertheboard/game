package com.game.common.world;

import java.io.Serializable;

/**
 * Represents a 64x64 tile chunk in the infinite world.
 * Chunks are identified by their chunk-space coordinates (chunkX, chunkY).
 * Each chunk contains CHUNK_SIZE * CHUNK_SIZE tiles.
 */
public class Chunk implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Number of tiles along each axis of a chunk. */
    public static final int CHUNK_SIZE = 64;

    private final int chunkX;
    private final int chunkY;
    private final Tile[][] tiles;

    public Chunk(int chunkX, int chunkY) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.tiles = new Tile[CHUNK_SIZE][CHUNK_SIZE];
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                this.tiles[x][y] = new Tile();
            }
        }
    }

    public int getChunkX() { return chunkX; }
    public int getChunkY() { return chunkY; }

    public Tile getTile(int localX, int localY) {
        if (localX < 0 || localX >= CHUNK_SIZE || localY < 0 || localY >= CHUNK_SIZE) {
            throw new IllegalArgumentException("Tile coords out of bounds: " + localX + "," + localY);
        }
        return tiles[localX][localY];
    }

    public void setTile(int localX, int localY, Tile tile) {
        if (localX < 0 || localX >= CHUNK_SIZE || localY < 0 || localY >= CHUNK_SIZE) {
            throw new IllegalArgumentException("Tile coords out of bounds: " + localX + "," + localY);
        }
        tiles[localX][localY] = tile;
    }

    public Tile[][] getTiles() { return tiles; }

    /** World tile X coordinate of the top-left corner of this chunk. */
    public int getWorldX() { return chunkX * CHUNK_SIZE; }

    /** World tile Y coordinate of the top-left corner of this chunk. */
    public int getWorldY() { return chunkY * CHUNK_SIZE; }

    @Override
    public String toString() {
        return "Chunk{chunkX=" + chunkX + ", chunkY=" + chunkY + "}";
    }
}
