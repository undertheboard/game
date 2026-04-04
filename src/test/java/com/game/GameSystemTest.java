package com.game;

import com.game.common.inventory.ItemDatabase;
import com.game.common.stats.*;
import com.game.common.world.*;
import com.game.server.ChunkGenerator;
import com.game.server.WorldManager;
import com.game.server.noise.OpenSimplexNoise;
import com.game.server.plugin.EventBus;
import com.game.server.plugin.events.PlayerChatEvent;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Core unit tests for all major game systems.
 */
class GameSystemTest {

    // -------------------------------------------------------------------------
    // OpenSimplex Noise
    // -------------------------------------------------------------------------

    @Test
    void noise_valueInRange() {
        OpenSimplexNoise noise = new OpenSimplexNoise(42L);
        for (int i = 0; i < 1000; i++) {
            double v = noise.noise2(i * 0.1, i * 0.07);
            assertTrue(v >= -2.0 && v <= 2.0, "Noise value out of expected range: " + v);
        }
    }

    @Test
    void noise_octaves_differentFromSingle() {
        OpenSimplexNoise noise = new OpenSimplexNoise(99L);
        double single = noise.noise2(1.0, 2.0);
        double octave = noise.noise2Octaves(1.0, 2.0, 4, 2.0, 0.5);
        // They should differ
        assertNotEquals(single, octave, 1e-10);
    }

    // -------------------------------------------------------------------------
    // Chunk / WorldManager
    // -------------------------------------------------------------------------

    @Test
    void chunk_sizeIsCorrect() {
        Chunk chunk = new Chunk(0, 0);
        assertEquals(Chunk.CHUNK_SIZE, 64);
        assertNotNull(chunk.getTile(0, 0));
        assertNotNull(chunk.getTile(63, 63));
    }

    @Test
    void chunk_outOfBounds_throws() {
        Chunk chunk = new Chunk(0, 0);
        assertThrows(IllegalArgumentException.class, () -> chunk.getTile(64, 0));
        assertThrows(IllegalArgumentException.class, () -> chunk.getTile(0, -1));
    }

    @Test
    void worldManager_generatesChunk() {
        WorldManager wm = new WorldManager(12345L);
        Chunk chunk = wm.getChunk(0, 0);
        assertNotNull(chunk);
        assertEquals(0, chunk.getChunkX());
        assertEquals(0, chunk.getChunkY());
        wm.shutdown();
    }

    @Test
    void worldManager_cachesChunks() {
        WorldManager wm = new WorldManager(42L);
        Chunk first  = wm.getChunk(0, 0);
        Chunk second = wm.getChunk(0, 0);
        assertSame(first, second, "Same chunk object should be returned from cache");
        wm.shutdown();
    }

    @Test
    void worldManager_placeTile() {
        WorldManager wm = new WorldManager(1L);
        wm.placeTile(5, 5, TileType.LAVA);
        Chunk chunk = wm.getChunk(0, 0);
        assertEquals(TileType.LAVA, chunk.getTile(5, 5).getType());
        wm.shutdown();
    }

    // -------------------------------------------------------------------------
    // ChunkGenerator biomes
    // -------------------------------------------------------------------------

    @Test
    void chunkGenerator_producesValidBiomes() {
        ChunkGenerator gen = new ChunkGenerator(777L);
        Chunk chunk = gen.generate(0, 0);
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                Tile tile = chunk.getTile(x, y);
                assertNotNull(tile.getType());
                assertNotNull(tile.getBiome());
            }
        }
    }

    @Test
    void chunkGenerator_differentSeedsProduceDifferentTerrain() {
        ChunkGenerator gen1 = new ChunkGenerator(100L);
        ChunkGenerator gen2 = new ChunkGenerator(200L);
        Chunk c1 = gen1.generate(5, 5);
        Chunk c2 = gen2.generate(5, 5);
        // Very unlikely that all tiles match with different seeds
        boolean allSame = true;
        for (int x = 0; x < Chunk.CHUNK_SIZE && allSame; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE && allSame; y++) {
                if (c1.getTile(x, y).getType() != c2.getTile(x, y).getType()) allSame = false;
            }
        }
        assertFalse(allSame, "Different seeds should produce different terrain");
    }

    // -------------------------------------------------------------------------
    // StatSheet & StatModifier
    // -------------------------------------------------------------------------

    @Test
    void statSheet_baseValues() {
        StatSheet sheet = new StatSheet();
        assertEquals(100.0, sheet.getFinalValue(StatType.MAX_HP), 0.01);
        assertEquals(10.0,  sheet.getFinalValue(StatType.ATTACK), 0.01);
    }

    @Test
    void statSheet_flatModifier() {
        StatSheet sheet = new StatSheet();
        sheet.addModifier(new StatModifier(StatType.ATTACK, 5, StatModifier.Operation.ADD_FLAT, "test"));
        assertEquals(15.0, sheet.getFinalValue(StatType.ATTACK), 0.01);
    }

    @Test
    void statSheet_percentModifier() {
        StatSheet sheet = new StatSheet();
        sheet.addModifier(new StatModifier(StatType.MAX_HP, 0.5, StatModifier.Operation.ADD_PERCENT, "test"));
        assertEquals(150.0, sheet.getFinalValue(StatType.MAX_HP), 0.01);
    }

    @Test
    void statSheet_multiplyModifier() {
        StatSheet sheet = new StatSheet();
        sheet.addModifier(new StatModifier(StatType.ATTACK, 1.0, StatModifier.Operation.MULTIPLY, "test"));
        // (10 + 0) * (1 + 0) * (1 + 1.0) = 20
        assertEquals(20.0, sheet.getFinalValue(StatType.ATTACK), 0.01);
    }

    @Test
    void statSheet_removeBySource() {
        StatSheet sheet = new StatSheet();
        sheet.addModifier(new StatModifier(StatType.ATTACK, 50, StatModifier.Operation.ADD_FLAT, "buff"));
        assertEquals(60.0, sheet.getFinalValue(StatType.ATTACK), 0.01);
        sheet.removeModifiersFromSource("buff");
        assertEquals(10.0, sheet.getFinalValue(StatType.ATTACK), 0.01);
    }

    // -------------------------------------------------------------------------
    // SkillTree
    // -------------------------------------------------------------------------

    @Test
    void skillTree_unlockTier1() {
        SkillTree tree = new SkillTree();
        StatSheet sheet = new StatSheet();
        tree.addPoints(5);
        assertTrue(tree.unlockSkill("POWER_I", sheet));
        assertEquals(15.0, sheet.getFinalValue(StatType.ATTACK), 0.01);
    }

    @Test
    void skillTree_prerequisiteEnforced() {
        SkillTree tree = new SkillTree();
        StatSheet sheet = new StatSheet();
        tree.addPoints(10);
        // Cannot unlock POWER_II without POWER_I
        assertFalse(tree.unlockSkill("POWER_II", sheet));
    }

    @Test
    void skillTree_pointsRequired() {
        SkillTree tree = new SkillTree();
        StatSheet sheet = new StatSheet();
        // No points added
        assertFalse(tree.unlockSkill("POWER_I", sheet));
    }

    // -------------------------------------------------------------------------
    // ItemDatabase
    // -------------------------------------------------------------------------

    @Test
    void itemDatabase_loadsItems() {
        ItemDatabase db = new ItemDatabase();
        assertTrue(db.size() > 0, "Item database should have at least one item");
    }

    @Test
    void itemDatabase_healingPotion() {
        ItemDatabase db = new ItemDatabase();
        var item = db.getItem("Healing_Potion");
        assertNotNull(item);
        assertEquals("Healing Potion", item.getName());
        assertTrue(item.hasTag(com.game.common.inventory.ItemTag.HEAL));
        assertTrue(item.isStackable());
    }

    @Test
    void itemDatabase_teleportStone() {
        ItemDatabase db = new ItemDatabase();
        var item = db.getItem("Teleport_Stone");
        assertNotNull(item);
        assertTrue(item.hasTag(com.game.common.inventory.ItemTag.TELEPORT));
    }

    // -------------------------------------------------------------------------
    // EventBus
    // -------------------------------------------------------------------------

    @Test
    void eventBus_handlerInvoked() {
        EventBus bus = new EventBus();
        boolean[] called = {false};
        bus.subscribe(PlayerChatEvent.class, e -> called[0] = true);
        bus.fire(new PlayerChatEvent("p1", "hello"));
        assertTrue(called[0]);
    }

    @Test
    void eventBus_cancelPreventsSubsequentHandlers() {
        EventBus bus = new EventBus();
        boolean[] secondCalled = {false};
        bus.subscribe(PlayerChatEvent.class, e -> e.setCancelled(true));
        bus.subscribe(PlayerChatEvent.class, e -> secondCalled[0] = true);
        bus.fire(new PlayerChatEvent("p1", "hello"));
        assertFalse(secondCalled[0]);
    }

    @Test
    void eventBus_messageCanBeModified() {
        EventBus bus = new EventBus();
        bus.subscribe(PlayerChatEvent.class, e -> e.setMessage("[filtered] " + e.getMessage()));
        PlayerChatEvent event = bus.fire(new PlayerChatEvent("p1", "hello"));
        assertEquals("[filtered] hello", event.getMessage());
    }
}
