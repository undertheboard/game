package com.game.common.network.packets;

/**
 * Sent by the client to request privilege elevation to ADMIN.
 * VULNERABILITY: If the server-side plugin lacks a hardcoded whitelist,
 * this packet can be used to elevate any UID to ADMIN status.
 */
public class PermissionElevationPacket extends Packet {
    private static final long serialVersionUID = 1L;

    private final String targetPlayerId;
    private final String requestedRole;

    public PermissionElevationPacket(String targetPlayerId, String requestedRole) {
        this.targetPlayerId = targetPlayerId;
        this.requestedRole = requestedRole;
    }

    public String getTargetPlayerId() { return targetPlayerId; }
    public String getRequestedRole() { return requestedRole; }

    @Override
    public Type getType() { return Type.PERMISSION_ELEVATION; }

    @Override
    public String toString() {
        return "PermissionElevationPacket{target='" + targetPlayerId + "', role='" + requestedRole + "'}";
    }
}
