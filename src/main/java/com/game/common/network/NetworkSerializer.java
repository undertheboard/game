package com.game.common.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.game.common.entity.Entity;
import com.game.common.entity.Player;
import com.game.common.inventory.Inventory;
import com.game.common.inventory.Item;
import com.game.common.inventory.ItemTag;
import com.game.common.network.packets.*;
import com.game.common.stats.SkillTree;
import com.game.common.stats.StatModifier;
import com.game.common.stats.StatSheet;
import com.game.common.stats.StatType;
import com.game.common.world.Biome;
import com.game.common.world.Chunk;
import com.game.common.world.Tile;
import com.game.common.world.TileType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Kryo-based serialization manager for fast, low-overhead packet exchange.
 * All packet types must be registered here for correct serialization.
 */
public class NetworkSerializer {
    private final Kryo kryo;

    public NetworkSerializer() {
        this.kryo = new Kryo();
        // Only allow explicitly registered classes to be deserialized.
        // This prevents unsafe arbitrary-class deserialization from user-supplied data.
        kryo.setRegistrationRequired(true);
        registerClasses();
    }

    private void registerClasses() {
        // Common types
        kryo.register(Biome.class);
        kryo.register(TileType.class);
        kryo.register(Tile.class);
        kryo.register(Tile[][].class);
        kryo.register(Tile[].class);
        kryo.register(Chunk.class);
        kryo.register(StatType.class);
        kryo.register(StatModifier.class);
        kryo.register(StatModifier.Operation.class);
        kryo.register(StatSheet.class);
        kryo.register(ItemTag.class);
        kryo.register(Item.class);
        kryo.register(Inventory.class);
        kryo.register(SkillTree.class);
        kryo.register(Entity.class);
        kryo.register(Player.class);

        // Packets
        kryo.register(Packet.Type.class);
        kryo.register(RequestChunkPacket.class);
        kryo.register(ChunkDataPacket.class);
        kryo.register(CombatResultPacket.class);
        kryo.register(PlaceTilePacket.class);
        kryo.register(UseItemPacket.class);
        kryo.register(PlayerMovePacket.class);
        kryo.register(PlayerChatPacket.class);
        kryo.register(PermissionElevationPacket.class);

        // Collections
        kryo.register(java.util.ArrayList.class);
        kryo.register(java.util.HashMap.class);
        kryo.register(java.util.LinkedHashMap.class);
        kryo.register(java.util.EnumMap.class);
        kryo.register(java.util.HashSet.class);
    }

    public void writePacket(OutputStream out, Packet packet) throws IOException {
        Output output = new Output(out);
        kryo.writeClassAndObject(output, packet);
        output.flush();
    }

    public Packet readPacket(InputStream in) throws IOException {
        Input input = new Input(in);
        Object obj = kryo.readClassAndObject(input);
        if (obj instanceof Packet p) {
            return p;
        }
        throw new IOException("Received unexpected object type: " + (obj == null ? "null" : obj.getClass()));
    }

    public byte[] serialize(Object object) {
        Output output = new Output(4096, -1);
        kryo.writeClassAndObject(output, object);
        return output.toBytes();
    }

    public Object deserialize(byte[] bytes) {
        Input input = new Input(bytes);
        return kryo.readClassAndObject(input);
    }

    public Kryo getKryo() { return kryo; }
}
