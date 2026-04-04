package com.game.server.plugin.events;

/**
 * Base class for all events dispatched on the server EventBus.
 */
public abstract class GameEvent {
    private boolean cancelled = false;

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public abstract String getEventName();
}
