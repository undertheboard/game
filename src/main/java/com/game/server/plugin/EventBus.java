package com.game.server.plugin;

import com.game.server.plugin.events.GameEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * High-performance, thread-safe EventBus for the plugin system.
 * Plugins subscribe handlers to specific event types. When an event is fired,
 * all registered handlers are invoked in registration order.
 *
 * <p>Handlers are stored in {@link CopyOnWriteArrayList} so that iteration is
 * always safe even when plugins add/remove handlers from within a handler.
 */
public class EventBus {
    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    /** Map from event class → ordered list of handlers. */
    private final Map<Class<? extends GameEvent>, CopyOnWriteArrayList<Consumer<? super GameEvent>>>
            handlers = new ConcurrentHashMap<>();

    /**
     * Registers a handler for events of type {@code eventType}.
     *
     * @param eventType the concrete event class to listen for
     * @param handler   callback invoked when the event fires
     * @param <E>       event type
     */
    @SuppressWarnings("unchecked")
    public <E extends GameEvent> void subscribe(Class<E> eventType, Consumer<E> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add((Consumer<? super GameEvent>) handler);
        LOGGER.fine("Subscribed handler to " + eventType.getSimpleName());
    }

    /**
     * Removes a specific handler from the bus.
     *
     * @param eventType the event type the handler was registered for
     * @param handler   the handler to remove
     */
    @SuppressWarnings("unchecked")
    public <E extends GameEvent> void unsubscribe(Class<E> eventType, Consumer<E> handler) {
        CopyOnWriteArrayList<Consumer<? super GameEvent>> list = handlers.get(eventType);
        if (list != null) {
            list.remove(handler);
        }
    }

    /**
     * Removes all handlers registered by a specific source tag.
     * Plugins should pass their plugin ID when subscribing so they can be
     * cleanly removed on hot-unload.
     */
    public void unsubscribeAll(Class<? extends GameEvent> eventType) {
        handlers.remove(eventType);
    }

    /**
     * Fires the event, invoking all registered handlers synchronously.
     * If a handler throws an exception it is logged and execution continues.
     *
     * @param event the event to dispatch
     * @return the (possibly mutated) event after all handlers have run
     */
    public <E extends GameEvent> E fire(E event) {
        CopyOnWriteArrayList<Consumer<? super GameEvent>> list = handlers.get(event.getClass());
        if (list == null) return event;

        for (Consumer<? super GameEvent> handler : list) {
            if (event.isCancelled()) break;
            try {
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.warning("Handler threw exception for " + event.getEventName() + ": " + e.getMessage());
            }
        }
        return event;
    }

    /** Returns the total number of registered handlers across all event types. */
    public int getTotalHandlerCount() {
        return handlers.values().stream().mapToInt(List::size).sum();
    }
}
