package com.redistricting.gui;

import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;

/**
 * Applies a dark colour scheme to Swing's default Metal look-and-feel by
 * overriding the {@link UIManager} colour keys used by panels, menus,
 * dialogs, text components and form controls.
 *
 * <p>Kept dependency-free (no FlatLaf or third-party LAF) so the bundled JAR
 * stays small and ships with no extra licences. The map panel reads the same
 * palette via {@link #BACKGROUND}/{@link #FOREGROUND} so it stays consistent.
 */
public final class DarkTheme {

    public static final Color BACKGROUND      = new Color(0x1E1E22);
    public static final Color BACKGROUND_ALT  = new Color(0x26262C);
    public static final Color BACKGROUND_HI   = new Color(0x2E2E36);
    public static final Color FOREGROUND      = new Color(0xE6E6EA);
    public static final Color FOREGROUND_DIM  = new Color(0xA0A0AA);
    public static final Color ACCENT          = new Color(0x6FA8FF);
    public static final Color BORDER          = new Color(0x3A3A42);
    public static final Color SELECTION       = new Color(0x375A8C);
    public static final Color MAP_OUTLINE     = new Color(0xC0C0C8);
    public static final Color MAP_BOUNDARY    = new Color(0xFFFFFF);

    private DarkTheme() {}

    /**
     * Install the dark palette into {@link UIManager}. Idempotent — call
     * once before constructing any Swing component.
     */
    public static void install() {
        // Cores
        put("Panel.background", BACKGROUND);
        put("OptionPane.background", BACKGROUND);
        put("RootPane.background", BACKGROUND);
        put("Viewport.background", BACKGROUND);
        put("ScrollPane.background", BACKGROUND);

        // Text + labels
        put("Label.foreground", FOREGROUND);
        put("Label.disabledForeground", FOREGROUND_DIM);
        put("OptionPane.messageForeground", FOREGROUND);
        put("TitledBorder.titleColor", FOREGROUND);

        // Inputs
        put("TextField.background", BACKGROUND_HI);
        put("TextField.foreground", FOREGROUND);
        put("TextField.caretForeground", FOREGROUND);
        put("TextField.selectionBackground", SELECTION);
        put("TextField.selectionForeground", FOREGROUND);
        put("FormattedTextField.background", BACKGROUND_HI);
        put("FormattedTextField.foreground", FOREGROUND);
        put("PasswordField.background", BACKGROUND_HI);
        put("PasswordField.foreground", FOREGROUND);
        put("TextArea.background", BACKGROUND_HI);
        put("TextArea.foreground", FOREGROUND);
        put("TextArea.caretForeground", FOREGROUND);
        put("TextArea.selectionBackground", SELECTION);
        put("TextArea.selectionForeground", FOREGROUND);
        put("EditorPane.background", BACKGROUND_HI);
        put("EditorPane.foreground", FOREGROUND);

        // Buttons & menus
        put("Button.background", BACKGROUND_ALT);
        put("Button.foreground", FOREGROUND);
        put("Button.select", SELECTION);
        put("Button.focus", ACCENT);
        put("ToggleButton.background", BACKGROUND_ALT);
        put("ToggleButton.foreground", FOREGROUND);
        put("MenuBar.background", BACKGROUND_ALT);
        put("MenuBar.foreground", FOREGROUND);
        put("Menu.background", BACKGROUND_ALT);
        put("Menu.foreground", FOREGROUND);
        put("Menu.selectionBackground", SELECTION);
        put("Menu.selectionForeground", FOREGROUND);
        put("MenuItem.background", BACKGROUND_ALT);
        put("MenuItem.foreground", FOREGROUND);
        put("MenuItem.selectionBackground", SELECTION);
        put("MenuItem.selectionForeground", FOREGROUND);
        put("MenuItem.disabledForeground", FOREGROUND_DIM);
        put("MenuItem.acceleratorForeground", FOREGROUND_DIM);
        put("MenuItem.acceleratorSelectionForeground", FOREGROUND);
        put("CheckBoxMenuItem.background", BACKGROUND_ALT);
        put("CheckBoxMenuItem.foreground", FOREGROUND);
        put("CheckBoxMenuItem.selectionBackground", SELECTION);
        put("CheckBoxMenuItem.selectionForeground", FOREGROUND);
        put("RadioButtonMenuItem.background", BACKGROUND_ALT);
        put("RadioButtonMenuItem.foreground", FOREGROUND);
        put("PopupMenu.background", BACKGROUND_ALT);
        put("PopupMenu.foreground", FOREGROUND);
        put("CheckBox.background", BACKGROUND);
        put("CheckBox.foreground", FOREGROUND);
        put("RadioButton.background", BACKGROUND);
        put("RadioButton.foreground", FOREGROUND);

        // Sliders / spinners / combos / tables
        put("Slider.background", BACKGROUND);
        put("Slider.foreground", FOREGROUND);
        put("Slider.tickColor", FOREGROUND_DIM);
        put("Spinner.background", BACKGROUND_HI);
        put("Spinner.foreground", FOREGROUND);
        put("ComboBox.background", BACKGROUND_HI);
        put("ComboBox.foreground", FOREGROUND);
        put("ComboBox.selectionBackground", SELECTION);
        put("ComboBox.selectionForeground", FOREGROUND);
        put("Table.background", BACKGROUND_HI);
        put("Table.foreground", FOREGROUND);
        put("Table.selectionBackground", SELECTION);
        put("Table.selectionForeground", FOREGROUND);
        put("TableHeader.background", BACKGROUND_ALT);
        put("TableHeader.foreground", FOREGROUND);
        put("List.background", BACKGROUND_HI);
        put("List.foreground", FOREGROUND);
        put("List.selectionBackground", SELECTION);
        put("List.selectionForeground", FOREGROUND);

        // Borders / dividers
        put("ToolTip.background", BACKGROUND_HI);
        put("ToolTip.foreground", FOREGROUND);
        put("Separator.foreground", BORDER);
        put("Separator.background", BORDER);
        put("SplitPane.background", BACKGROUND);
        put("ScrollBar.background", BACKGROUND_ALT);
        put("ScrollBar.foreground", FOREGROUND_DIM);
        put("ScrollBar.thumb", BACKGROUND_HI);
        put("ScrollBar.thumbHighlight", BORDER);
        put("ScrollBar.thumbShadow", BORDER);
        put("ScrollBar.track", BACKGROUND_ALT);

        // Dialog title bars (cross-platform fall-through; native title bar on
        // Windows is controlled by the OS, but we set the icon background so
        // any custom JFrame.setUndecorated paths look right too).
        put("InternalFrame.background", BACKGROUND);
        put("InternalFrame.activeTitleBackground", BACKGROUND_ALT);
        put("InternalFrame.activeTitleForeground", FOREGROUND);
        put("InternalFrame.inactiveTitleBackground", BACKGROUND_ALT);
        put("InternalFrame.inactiveTitleForeground", FOREGROUND_DIM);
        put("InternalFrame.borderColor", BORDER);
    }

    private static void put(String key, Color c) {
        UIManager.put(key, new ColorUIResource(c));
    }
}
