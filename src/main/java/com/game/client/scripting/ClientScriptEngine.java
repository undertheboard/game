package com.game.client.scripting;

import com.game.common.entity.Player;
import com.game.common.network.packets.*;

import javax.script.*;
import java.io.*;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaScript scripting engine for the client using Nashorn (via nashorn-core).
 *
 * <p>Scripts can automate gameplay by calling the injected {@code game} API
 * object. Example script:
 * <pre>
 * // Auto-heal when HP drops below 20%
 * if (game.getHpPercent() < 0.20) {
 *     game.useItem("Healing_Potion");
 * }
 * </pre>
 *
 * <p>Scripts are loaded from the {@code scripts/} directory. They can be
 * executed individually or on every client tick.
 */
public class ClientScriptEngine {
    private static final Logger LOGGER = Logger.getLogger(ClientScriptEngine.class.getName());

    private final ScriptEngine engine;
    private final ScriptContext context;
    private final GameScriptApi gameApi;

    public ClientScriptEngine(GameScriptApi api) {
        this.gameApi = api;

        // Use Nashorn (nashorn-core artifact) via the standard ScriptEngineManager
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine se = manager.getEngineByName("nashorn");
        if (se == null) {
            // Fallback: try "javascript" alias
            se = manager.getEngineByName("javascript");
        }
        if (se == null) {
            LOGGER.warning("No JavaScript engine found. Scripting will be disabled.");
        }
        this.engine = se;
        this.context = se != null ? se.getContext() : null;

        if (engine != null) {
            engine.put("game", gameApi);
        }
    }

    /**
     * Executes the given script string.
     *
     * @param script JavaScript source
     * @return the result of the last expression, or null
     */
    public Object eval(String script) {
        if (engine == null) return null;
        try {
            return engine.eval(script);
        } catch (ScriptException e) {
            LOGGER.warning("Script error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Loads and executes a script file from the {@code scripts/} directory.
     *
     * @param filename file name within the scripts directory
     */
    public Object evalFile(String filename) {
        if (engine == null) return null;
        Path path = Paths.get("scripts", filename);
        if (!Files.exists(path)) {
            LOGGER.warning("Script file not found: " + path);
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return engine.eval(reader);
        } catch (IOException | ScriptException e) {
            LOGGER.log(Level.WARNING, "Failed to execute script: " + filename, e);
            return null;
        }
    }

    /**
     * Runs a tick automation script if one is loaded.
     * Call this once per client render tick.
     */
    public void tickEval() {
        Path autoScript = Paths.get("scripts", "auto.js");
        if (Files.exists(autoScript)) {
            evalFile("auto.js");
        }
    }

    public boolean isAvailable() { return engine != null; }
    public GameScriptApi getGameApi() { return gameApi; }

    // -------------------------------------------------------------------------
    // Script API
    // -------------------------------------------------------------------------

    /**
     * The {@code game} object exposed to scripts.
     * Provides safe, high-level access to game state and actions.
     */
    public interface GameScriptApi {
        /** Returns current HP as a percentage [0.0, 1.0]. */
        double getHpPercent();

        /** Uses an item from the inventory. */
        void useItem(String itemId);

        /** Moves the player to the given world coordinates. */
        void moveTo(double x, double y);

        /** Sends a chat message. */
        void chat(String message);

        /** Returns current player X position. */
        double getX();

        /** Returns current player Y position. */
        double getY();

        /** Returns the current tick count. */
        long getTick();
    }
}
