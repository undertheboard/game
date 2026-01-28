package com.undertheboard.game.common;

import java.io.Serializable;

/**
 * Base class for all network messages between client and server.
 */
public abstract class NetworkMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum MessageType {
        PLAYER_JOIN,
        PLAYER_LEAVE,
        PLAYER_MOVE,
        GAME_STATE,
        SERVER_ANNOUNCE,
        DISCOVER_REQUEST
    }
    
    private MessageType type;
    private long timestamp;
    
    public NetworkMessage(MessageType type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    
    public MessageType getType() {
        return type;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}
