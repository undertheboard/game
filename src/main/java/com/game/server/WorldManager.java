package com.game.server;

import com.game.common.world.Chunk;
import com.game.common.world.Tile;
import com.game.common.world.TileType;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Manages the infinite world by loading/generating chunks on demand.
 *
 * <p>Chunk generation is offloaded to a {@link ForkJoinPool} so the server tick
 * loop is never blocked by expensive noise computation.  Generated chunks are
 * kept in an LRU-style {@link LinkedHashMap} capped at {@code MAX_LOADED_CHUNKS}.
 *
 * <p><b>Design note (intentional flaw):</b> The server accepts any chunk
 * coordinates supplied by the client, including extremely large values.  A
 * malicious client can therefore request millions of distinct chunks and force
 * the server to generate terrain far outside normal play area.
 */
public class WorldManager {
    private static final Logger LOGGER = Logger.getLogger(WorldManager.class.getName());

    /** Maximum chunks kept in memory before evicting the oldest. */
    private static final int MAX_LOADED_CHUNKS = 1024;

    private final ChunkGenerator generator;
    private final ForkJoinPool generationPool;

    /** Thread-safe LRU chunk cache. */
    private final Map<ChunkKey, Chunk> loadedChunks;

    public WorldManager(long worldSeed) {
        this.generator = new ChunkGenerator(worldSeed);
        this.generationPool = new ForkJoinPool(
                Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        this.loadedChunks = Collections.synchronizedMap(
                new java.util.LinkedHashMap<>(MAX_LOADED_CHUNKS, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<ChunkKey, Chunk> eldest) {
                        return size() > MAX_LOADED_CHUNKS;
                    }
                });
    }

    /**
     * Returns the chunk at (chunkX, chunkY).  If not yet generated, generates it
     * synchronously on the calling thread and caches it.
     */
    public Chunk getChunk(int chunkX, int chunkY) {
        ChunkKey key = new ChunkKey(chunkX, chunkY);
        return loadedChunks.computeIfAbsent(key, k -> generateChunk(k.x, k.y));
    }

    /**
     * Asynchronously pre-generates a chunk.  Returns a Future that resolves to
     * the generated Chunk.
     */
    public CompletableFuture<Chunk> getChunkAsync(int chunkX, int chunkY) {
        ChunkKey key = new ChunkKey(chunkX, chunkY);
        Chunk existing = loadedChunks.get(key);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        return CompletableFuture.supplyAsync(() -> {
            Chunk chunk = generateChunk(chunkX, chunkY);
            loadedChunks.put(key, chunk);
            return chunk;
        }, generationPool);
    }

    private Chunk generateChunk(int chunkX, int chunkY) {
        LOGGER.fine("Generating chunk (" + chunkX + ", " + chunkY + ")");
        return generator.generate(chunkX, chunkY);
    }

    /**
     * Places a tile in the world at the given world-space tile coordinates.
     * The appropriate chunk is loaded/generated if needed.
     */
    public void placeTile(int worldX, int worldY, TileType tileType) {
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(worldY, Chunk.CHUNK_SIZE);
        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localY = Math.floorMod(worldY, Chunk.CHUNK_SIZE);

        Chunk chunk = getChunk(chunkX, chunkY);
        Tile existing = chunk.getTile(localX, localY);
        chunk.setTile(localX, localY, new Tile(tileType, existing.getBiome(), existing.getElevation()));
    }

    public int getLoadedChunkCount() {
        return loadedChunks.size();
    }

    public void shutdown() {
        generationPool.shutdown();
    }

    /** Value-based key for chunk coordinates. */
    private static final class ChunkKey {
        final int x;
        final int y;

        ChunkKey(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkKey other)) return false;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }
}
