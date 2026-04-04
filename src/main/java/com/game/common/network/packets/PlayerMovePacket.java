package com.game.common.network.packets;

/**
 * Sent by the client to move the player.
 */
public class PlayerMovePacket extends Packet {
    private static final long serialVersionUID = 1L;

    private final double x;
    private final double y;

    public PlayerMovePacket(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() { return x; }
    public double getY() { return y; }

    @Override
    public Type getType() { return Type.PLAYER_MOVE; }

    @Override
    public String toString() {
        return "PlayerMovePacket{x=" + x + ", y=" + y + "}";
    }
}
