package com.game.server.plugin.events;

/**
 * Fired when an entity (player or mob) dies.
 */
public class EntityDeathEvent extends GameEvent {
    private final String entityId;
    private final String entityName;
    private final String killerId;

    public EntityDeathEvent(String entityId, String entityName, String killerId) {
        this.entityId = entityId;
        this.entityName = entityName;
        this.killerId = killerId;
    }

    public String getEntityId() { return entityId; }
    public String getEntityName() { return entityName; }
    public String getKillerId() { return killerId; }

    @Override
    public String getEventName() { return "EntityDeathEvent"; }
}
