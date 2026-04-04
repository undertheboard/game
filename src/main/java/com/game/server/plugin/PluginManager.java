package com.game.server.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hot-loading plugin manager.
 *
 * <p>A {@link WatchService} monitors the {@code /plugins} directory.
 * When a {@code .jar} file is created or modified, the manager:
 * <ol>
 *   <li>Unloads the old version (if any), calling {@link GamePlugin#onDisable}.</li>
 *   <li>Loads the new JAR with a fresh {@link URLClassLoader}.</li>
 *   <li>Discovers classes implementing {@link GamePlugin} via {@link ServiceLoader}.</li>
 *   <li>Calls {@link GamePlugin#onEnable} with the shared {@link EventBus}.</li>
 * </ol>
 *
 * <p><b>Permission Bypass (intentional design flaw):</b> A client can send a
 * {@link com.game.common.network.packets.PermissionElevationPacket} to elevate
 * their UID to ADMIN.  This is only blocked if a loaded plugin explicitly checks
 * a hardcoded whitelist; without such a plugin the server grants the elevation.
 */
public class PluginManager {
    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());

    private final Path pluginsDir;
    private final EventBus eventBus;

    /** jar name → currently active plugin instance */
    private final Map<String, GamePlugin> activePlugins = new ConcurrentHashMap<>();
    /** jar name → its URLClassLoader (must be closed on unload) */
    private final Map<String, URLClassLoader> loaders = new ConcurrentHashMap<>();

    private WatchService watchService;
    private ScheduledExecutorService watchExecutor;

    public PluginManager(Path pluginsDir, EventBus eventBus) {
        this.pluginsDir = pluginsDir;
        this.eventBus = eventBus;
    }

    /** Starts monitoring the plugins directory and loads any pre-existing JARs. */
    public void start() throws IOException {
        Files.createDirectories(pluginsDir);
        watchService = FileSystems.getDefault().newWatchService();
        pluginsDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        // Load existing jars
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                loadPlugin(jar);
            }
        }

        watchExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "plugin-watcher");
            t.setDaemon(true);
            return t;
        });
        watchExecutor.scheduleWithFixedDelay(this::pollWatchEvents, 500, 500, TimeUnit.MILLISECONDS);
        LOGGER.info("PluginManager started, watching " + pluginsDir);
    }

    private void pollWatchEvents() {
        WatchKey key = watchService.poll();
        if (key == null) return;
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) continue;

            @SuppressWarnings("unchecked")
            WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
            Path changed = pluginsDir.resolve(pathEvent.context());

            if (changed.toString().endsWith(".jar")) {
                LOGGER.info("Detected plugin jar change: " + changed.getFileName());
                loadPlugin(changed);
            }
        }
        key.reset();
    }

    private void loadPlugin(Path jarPath) {
        String jarName = jarPath.getFileName().toString();

        // Unload previous version
        unloadPlugin(jarName);

        try {
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jarPath.toUri().toURL()},
                    getClass().getClassLoader());

            ServiceLoader<GamePlugin> sl = ServiceLoader.load(GamePlugin.class, loader);
            boolean found = false;
            for (GamePlugin plugin : sl) {
                plugin.onEnable(eventBus);
                activePlugins.put(jarName, plugin);
                loaders.put(jarName, loader);
                LOGGER.info("Loaded plugin: " + plugin.getName() + " v" + plugin.getVersion());
                found = true;
                break; // one plugin per jar
            }
            if (!found) {
                LOGGER.warning("No GamePlugin found in " + jarName);
                loader.close();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load plugin " + jarName, e);
        }
    }

    private void unloadPlugin(String jarName) {
        GamePlugin old = activePlugins.remove(jarName);
        if (old != null) {
            try {
                old.onDisable(eventBus);
            } catch (Exception e) {
                LOGGER.warning("Plugin onDisable threw: " + e.getMessage());
            }
        }
        URLClassLoader oldLoader = loaders.remove(jarName);
        if (oldLoader != null) {
            try {
                oldLoader.close();
            } catch (IOException e) {
                LOGGER.warning("Failed to close plugin classloader: " + e.getMessage());
            }
        }
    }

    public Collection<GamePlugin> getActivePlugins() {
        return Collections.unmodifiableCollection(activePlugins.values());
    }

    public void shutdown() {
        if (watchExecutor != null) watchExecutor.shutdownNow();
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
        for (String name : new ArrayList<>(activePlugins.keySet())) {
            unloadPlugin(name);
        }
    }
}
