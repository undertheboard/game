package com.undertheboard.game.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the complete game state including all players.
 * Thread-safe for concurrent access from multiple threads.
 */
public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Map<String, PlayerModel> players;
    private long timestamp;
    
    public GameState() {
        this.players = new ConcurrentHashMap<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    public void addPlayer(PlayerModel player) {
        players.put(player.getId(), player);
        this.timestamp = System.currentTimeMillis();
    }
    
    public void removePlayer(String playerId) {
        players.remove(playerId);
        this.timestamp = System.currentTimeMillis();
    }
    
    public PlayerModel getPlayer(String playerId) {
        return players.get(playerId);
    }
    
    public Map<String, PlayerModel> getPlayers() {
        return players;
    }
    
    public void updatePlayer(String playerId, float targetX, float targetY) {
        PlayerModel player = players.get(playerId);
        if (player != null) {
            player.setTarget(targetX, targetY);
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public void update() {
        for (PlayerModel player : players.values()) {
            player.update();
        }
        this.timestamp = System.currentTimeMillis();
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}
