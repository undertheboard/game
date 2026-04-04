package com.game.common.network.packets;

/**
 * Chat message sent by a player.
 */
public class PlayerChatPacket extends Packet {
    private static final long serialVersionUID = 1L;

    private final String playerId;
    private final String message;

    public PlayerChatPacket(String playerId, String message) {
        this.playerId = playerId;
        this.message = message;
    }

    public String getPlayerId() { return playerId; }
    public String getMessage() { return message; }

    @Override
    public Type getType() { return Type.PLAYER_CHAT; }

    @Override
    public String toString() {
        return "PlayerChatPacket{playerId='" + playerId + "', message='" + message + "'}";
    }
}
