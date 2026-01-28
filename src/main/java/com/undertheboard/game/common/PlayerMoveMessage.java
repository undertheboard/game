package com.undertheboard.game.common;

/**
 * Message sent by client when moving the player.
 */
public class PlayerMoveMessage extends NetworkMessage {
    private static final long serialVersionUID = 1L;
    
    private String playerId;
    private float targetX;
    private float targetY;
    
    public PlayerMoveMessage(String playerId, float targetX, float targetY) {
        super(MessageType.PLAYER_MOVE);
        this.playerId = playerId;
        this.targetX = targetX;
        this.targetY = targetY;
    }
    
    public String getPlayerId() {
        return playerId;
    }
    
    public float getTargetX() {
        return targetX;
    }
    
    public float getTargetY() {
        return targetY;
    }
}
