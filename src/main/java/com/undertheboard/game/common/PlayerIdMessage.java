package com.undertheboard.game.common;

/**
 * Message sent by server to client upon successful join with assigned player ID.
 */
public class PlayerIdMessage extends NetworkMessage {
    private static final long serialVersionUID = 1L;
    
    private String playerId;
    
    public PlayerIdMessage(String playerId) {
        super(MessageType.PLAYER_JOIN);
        this.playerId = playerId;
    }
    
    public String getPlayerId() {
        return playerId;
    }
}
