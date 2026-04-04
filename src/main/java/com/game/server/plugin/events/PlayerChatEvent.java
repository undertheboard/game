package com.game.server.plugin.events;

/**
 * Fired when a player sends a chat message.
 */
public class PlayerChatEvent extends GameEvent {
    private final String playerId;
    private String message;

    public PlayerChatEvent(String playerId, String message) {
        this.playerId = playerId;
        this.message = message;
    }

    public String getPlayerId() { return playerId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public String getEventName() { return "PlayerChatEvent"; }
}
