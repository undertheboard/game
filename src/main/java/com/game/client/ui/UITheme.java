package com.game.client.ui;

import java.awt.Color;
import java.awt.Font;
import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * CSS-like skinnable UI theme loaded from a local {@code theme.properties} file.
 *
 * <p>Supported properties (all optional; defaults apply):
 * <pre>
 * background=#1A1A2E
 * panel=#16213E
 * accent=#0F3460
 * button=#E94560
 * buttonHover=#FF6B8A
 * text=#EAEAEA
 * fontFamily=SansSerif
 * fontSize=13
 * playerColor=#FFD700
 * </pre>
 */
public class UITheme {
    private static final Logger LOGGER = Logger.getLogger(UITheme.class.getName());
    private static final String THEME_FILE = "theme.properties";

    private Color background  = new Color(0x1A1A2E);
    private Color panel       = new Color(0x16213E);
    private Color accent      = new Color(0x0F3460);
    private Color button      = new Color(0xE94560);
    private Color buttonHover = new Color(0xFF6B8A);
    private Color text        = new Color(0xEAEAEA);
    private Color playerColor = new Color(0xFFD700);
    private String fontFamily = "SansSerif";
    private int fontSize      = 13;

    public UITheme() {
        load();
    }

    /** Reloads the theme from disk (called when the theme file changes). */
    public void reload() {
        load();
    }

    private void load() {
        Path p = Paths.get(THEME_FILE);
        if (!Files.exists(p)) {
            LOGGER.fine("No theme.properties found, using defaults.");
            return;
        }
        Map<String, String> props = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                props.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to read theme file: " + e.getMessage());
            return;
        }

        background  = parseColor(props, "background",  background);
        panel       = parseColor(props, "panel",        panel);
        accent      = parseColor(props, "accent",       accent);
        button      = parseColor(props, "button",       button);
        buttonHover = parseColor(props, "buttonHover",  buttonHover);
        text        = parseColor(props, "text",         text);
        playerColor = parseColor(props, "playerColor",  playerColor);
        fontFamily  = props.getOrDefault("fontFamily", fontFamily);
        try {
            fontSize = Integer.parseInt(props.getOrDefault("fontSize", String.valueOf(fontSize)));
        } catch (NumberFormatException ignored) {}

        LOGGER.info("UI theme loaded from " + THEME_FILE);
    }

    private Color parseColor(Map<String, String> props, String key, Color fallback) {
        String val = props.get(key);
        if (val == null) return fallback;
        try {
            return Color.decode(val.startsWith("#") ? val : "#" + val);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid color for '" + key + "': " + val);
            return fallback;
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Color backgroundColor() { return background; }
    public Color panelColor()      { return panel; }
    public Color accentColor()     { return accent; }
    public Color buttonColor()     { return button; }
    public Color buttonHoverColor(){ return buttonHover; }
    public Color textColor()       { return text; }
    public Color playerColor()     { return playerColor; }
    public String fontFamily()     { return fontFamily; }
    public int fontSize()          { return fontSize; }
    public Font defaultFont()      { return new Font(fontFamily, Font.PLAIN, fontSize); }
    public Font boldFont()         { return new Font(fontFamily, Font.BOLD, fontSize); }
}
