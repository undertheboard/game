package com.game.common.network.packets;

/**
 * Sent by the client to use an item from the inventory.
 */
public class UseItemPacket extends Packet {
    private static final long serialVersionUID = 1L;

    private final String itemId;
    private final String targetEntityId;

    public UseItemPacket(String itemId, String targetEntityId) {
        this.itemId = itemId;
        this.targetEntityId = targetEntityId;
    }

    public String getItemId() { return itemId; }
    public String getTargetEntityId() { return targetEntityId; }

    @Override
    public Type getType() { return Type.USE_ITEM; }

    @Override
    public String toString() {
        return "UseItemPacket{itemId='" + itemId + "', target='" + targetEntityId + "'}";
    }
}
