package com.game.common.network.packets;

import java.io.Serializable;

/**
 * Base class for all network packets exchanged between client and server.
 */
public abstract class Packet implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        REQUEST_CHUNK,
        CHUNK_DATA,
        COMBAT_RESULT,
        PLACE_TILE,
        USE_ITEM,
        PLAYER_MOVE,
        PLAYER_CHAT,
        PERMISSION_ELEVATION,
        HANDSHAKE,
        DISCONNECT
    }

    public abstract Type getType();
}
