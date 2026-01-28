package com.undertheboard.game.common;

/**
 * Message sent by server to broadcast game state to all clients.
 */
public class GameStateMessage extends NetworkMessage {
    private static final long serialVersionUID = 1L;
    
    private GameState gameState;
    
    public GameStateMessage(GameState gameState) {
        super(MessageType.GAME_STATE);
        this.gameState = gameState;
    }
    
    public GameState getGameState() {
        return gameState;
    }
}
