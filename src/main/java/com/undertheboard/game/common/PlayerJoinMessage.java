package com.undertheboard.game.common;

/**
 * Message sent by client to join the game.
 */
public class PlayerJoinMessage extends NetworkMessage {
    private static final long serialVersionUID = 1L;
    
    private String playerName;
    
    public PlayerJoinMessage(String playerName) {
        super(MessageType.PLAYER_JOIN);
        this.playerName = playerName;
    }
    
    public String getPlayerName() {
        return playerName;
    }
}
