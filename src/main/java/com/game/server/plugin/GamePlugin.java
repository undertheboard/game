package com.game.server.plugin;

import java.util.Set;

/**
 * Interface that all server plugins must implement.
 * Plugins are loaded dynamically from JAR files in the /plugins folder.
 */
public interface GamePlugin {
    /** Unique identifier for this plugin. */
    String getId();

    /** Human-readable name. */
    String getName();

    /** Plugin version string. */
    String getVersion();

    /** The set of permissions this plugin requires. */
    Set<PluginPermission> getRequiredPermissions();

    /** Called once when the plugin is loaded/hot-reloaded. */
    void onEnable(EventBus eventBus);

    /** Called once when the plugin is unloaded. */
    void onDisable(EventBus eventBus);
}
