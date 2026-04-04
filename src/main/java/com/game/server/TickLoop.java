package com.game.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Single-threaded game tick loop.
 *
 * <p>Runs all registered {@code tickHandlers} at a fixed tick rate (default 20 TPS).
 * Because the loop is single-threaded, game state mutations within handlers are
 * inherently race-condition-free.  Work that is expensive (e.g., chunk generation)
 * must be deferred to the {@link WorldManager}'s {@link java.util.concurrent.ForkJoinPool}.
 */
public class TickLoop implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(TickLoop.class.getName());

    /** Target ticks per second. */
    public static final int TPS = 20;
    private static final long TICK_DURATION_NS = 1_000_000_000L / TPS;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Consumer<Long>> tickHandlers = new ArrayList<>();
    private long tickCount = 0;

    /** Registers a handler that is called once per tick with the current tick number. */
    public void addTickHandler(Consumer<Long> handler) {
        tickHandlers.add(handler);
    }

    public void removeTickHandler(Consumer<Long> handler) {
        tickHandlers.remove(handler);
    }

    public void start() {
        running.set(true);
    }

    public void stop() {
        running.set(false);
    }

    public boolean isRunning() { return running.get(); }
    public long getTickCount() { return tickCount; }

    @Override
    public void run() {
        LOGGER.info("TickLoop started at " + TPS + " TPS");
        long lastTime = System.nanoTime();

        while (running.get()) {
            long now = System.nanoTime();
            long elapsed = now - lastTime;

            if (elapsed >= TICK_DURATION_NS) {
                lastTime = now;
                tick(tickCount++);
            } else {
                // Sleep for remainder of tick to avoid busy-waiting
                long sleepNs = TICK_DURATION_NS - elapsed;
                try {
                    Thread.sleep(sleepNs / 1_000_000L, (int) (sleepNs % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        LOGGER.info("TickLoop stopped after " + tickCount + " ticks");
    }

    private void tick(long tickNumber) {
        for (Consumer<Long> handler : tickHandlers) {
            try {
                handler.accept(tickNumber);
            } catch (Exception e) {
                LOGGER.warning("Tick handler threw exception on tick " + tickNumber + ": " + e.getMessage());
            }
        }
    }
}
