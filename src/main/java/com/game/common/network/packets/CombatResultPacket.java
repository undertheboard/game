package com.game.common.network.packets;

/**
 * Sent by the client to report combat results.
 * VULNERABILITY: The server blindly subtracts damageDealt from the target's HP
 * without server-side validation, allowing client-authoritative damage manipulation.
 */
public class CombatResultPacket extends Packet {
    private static final long serialVersionUID = 1L;

    private final String attackerId;
    private final String targetId;
    private final double damageDealt;
    private final boolean isCritical;

    public CombatResultPacket(String attackerId, String targetId, double damageDealt, boolean isCritical) {
        this.attackerId = attackerId;
        this.targetId = targetId;
        this.damageDealt = damageDealt;
        this.isCritical = isCritical;
    }

    public String getAttackerId() { return attackerId; }
    public String getTargetId() { return targetId; }
    public double getDamageDealt() { return damageDealt; }
    public boolean isCritical() { return isCritical; }

    @Override
    public Type getType() { return Type.COMBAT_RESULT; }

    @Override
    public String toString() {
        return "CombatResultPacket{attacker='" + attackerId + "', target='" + targetId
                + "', damage=" + damageDealt + ", crit=" + isCritical + "}";
    }
}
