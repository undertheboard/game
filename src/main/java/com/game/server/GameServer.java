package com.game.server;

import com.game.common.entity.Player;
import com.game.common.network.NetworkSerializer;
import com.game.common.network.packets.*;
import com.game.common.world.Chunk;
import com.game.server.plugin.EventBus;
import com.game.server.plugin.PluginManager;
import com.game.server.plugin.events.EntityDeathEvent;
import com.game.server.plugin.events.PlayerChatEvent;
import com.game.server.plugin.events.WorldChangeEvent;

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main game server.
 *
 * <p>Accepts TCP connections from clients. Each client is handled on its own I/O
 * thread; all game-logic mutations are forwarded to the single-threaded
 * {@link TickLoop} via a task queue.
 *
 * <p>World generation is delegated to the {@link WorldManager} which uses a
 * {@link java.util.concurrent.ForkJoinPool} internally.
 */
public class GameServer {
    private static final Logger LOGGER = Logger.getLogger(GameServer.class.getName());
    public static final int DEFAULT_PORT = 7777;

    private final int port;
    private final WorldManager worldManager;
    private final TickLoop tickLoop;
    private final EventBus eventBus;
    private final PluginManager pluginManager;
    private final NetworkSerializer serializer;

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Player> players = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private ExecutorService ioPool;
    private Thread tickThread;
    private volatile boolean running = false;

    public GameServer(int port, long worldSeed) {
        this.port = port;
        this.worldManager = new WorldManager(worldSeed);
        this.tickLoop = new TickLoop();
        this.eventBus = new EventBus();
        this.serializer = new NetworkSerializer();

        Path pluginsDir = Paths.get("plugins");
        this.pluginManager = new PluginManager(pluginsDir, eventBus);

        setupTickHandlers();
    }

    private void setupTickHandlers() {
        tickLoop.addTickHandler(tick -> {
            // Periodic logging every 5 seconds
            if (tick % (TickLoop.TPS * 5) == 0) {
                LOGGER.fine("Tick " + tick + " | Players: " + clients.size()
                        + " | Loaded chunks: " + worldManager.getLoadedChunkCount());
            }
        });
    }

    public void start() throws IOException {
        running = true;
        serverSocket = new ServerSocket(port);
        ioPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "client-io-" + UUID.randomUUID().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });

        try {
            pluginManager.start();
        } catch (IOException e) {
            LOGGER.warning("Could not start plugin manager: " + e.getMessage());
        }

        tickLoop.start();
        tickThread = new Thread(tickLoop, "game-tick");
        tickThread.setDaemon(true);
        tickThread.start();

        LOGGER.info("GameServer listening on port " + port);
        acceptLoop();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                String connId = UUID.randomUUID().toString();
                ClientHandler handler = new ClientHandler(connId, socket);
                clients.put(connId, handler);
                ioPool.execute(handler);
                LOGGER.info("Client connected: " + connId + " from " + socket.getInetAddress());
            } catch (IOException e) {
                if (running) {
                    LOGGER.log(Level.WARNING, "Accept error", e);
                }
            }
        }
    }

    public void stop() throws IOException {
        running = false;
        tickLoop.stop();
        pluginManager.shutdown();
        worldManager.shutdown();
        if (serverSocket != null) serverSocket.close();
        if (ioPool != null) ioPool.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Packet handlers
    // -------------------------------------------------------------------------

    private void handleRequestChunk(String connId, RequestChunkPacket pkt) {
        // NOTE: No coordinate bounds check — intentional flaw from design spec.
        worldManager.getChunkAsync(pkt.getChunkX(), pkt.getChunkY())
                .thenAccept(chunk -> {
                    ClientHandler client = clients.get(connId);
                    if (client != null) {
                        client.send(new ChunkDataPacket(chunk));
                    }
                });
    }

    private void handleCombatResult(String connId, CombatResultPacket pkt) {
        // VULNERABILITY: damage is taken at face value from the client.
        Player target = players.get(pkt.getTargetId());
        if (target == null) return;
        target.applyDamage(pkt.getDamageDealt());
        LOGGER.info("Combat: " + pkt.getAttackerId() + " dealt " + pkt.getDamageDealt()
                + " to " + pkt.getTargetId() + " (HP now: " + target.getHp() + ")");

        if (!target.isAlive()) {
            EntityDeathEvent event = new EntityDeathEvent(
                    target.getId(), target.getName(), pkt.getAttackerId());
            eventBus.fire(event);
        }
    }

    private void handlePlaceTile(String connId, PlaceTilePacket pkt) {
        worldManager.placeTile(pkt.getWorldX(), pkt.getWorldY(), pkt.getTileType());
        WorldChangeEvent event = new WorldChangeEvent(
                pkt.getWorldX(), pkt.getWorldY(), pkt.getTileType(), connId);
        eventBus.fire(event);
    }

    private void handlePlayerChat(String connId, PlayerChatPacket pkt) {
        PlayerChatEvent event = new PlayerChatEvent(pkt.getPlayerId(), pkt.getMessage());
        eventBus.fire(event);
        if (!event.isCancelled()) {
            // Broadcast to all clients
            PlayerChatPacket broadcast = new PlayerChatPacket(pkt.getPlayerId(), event.getMessage());
            clients.values().forEach(c -> c.send(broadcast));
        }
    }

    private void handlePermissionElevation(String connId, PermissionElevationPacket pkt) {
        // VULNERABILITY: Without a plugin whitelist check, elevation is granted.
        Player player = players.get(pkt.getTargetPlayerId());
        if (player != null) {
            LOGGER.warning("Permission elevation requested for " + pkt.getTargetPlayerId()
                    + " → " + pkt.getRequestedRole());
            player.setRole(pkt.getRequestedRole());
        }
    }

    private void handlePlayerMove(String connId, PlayerMovePacket pkt) {
        Player player = players.get(connId);
        if (player != null) {
            player.setX(pkt.getX());
            player.setY(pkt.getY());
        }
    }

    // -------------------------------------------------------------------------
    // Client handler (runs on I/O thread)
    // -------------------------------------------------------------------------

    private class ClientHandler implements Runnable {
        private final String connId;
        private final Socket socket;
        private OutputStream out;

        ClientHandler(String connId, Socket socket) {
            this.connId = connId;
            this.socket = socket;
        }

        @Override
        public void run() {
            // Create a new player for this connection
            Player player = new Player("Player-" + connId.substring(0, 6), 0, 0);
            player.setConnectionId(connId);
            players.put(connId, player);

            try {
                out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                while (running && !socket.isClosed()) {
                    Packet packet = serializer.readPacket(in);
                    dispatchPacket(connId, packet);
                }
            } catch (IOException e) {
                LOGGER.fine("Client " + connId + " disconnected: " + e.getMessage());
            } finally {
                clients.remove(connId);
                players.remove(connId);
                try { socket.close(); } catch (IOException ignored) {}
                LOGGER.info("Client disconnected: " + connId);
            }
        }

        void send(Packet packet) {
            if (out == null || socket.isClosed()) return;
            try {
                serializer.writePacket(out, packet);
            } catch (IOException e) {
                LOGGER.fine("Send error to " + connId + ": " + e.getMessage());
            }
        }
    }

    private void dispatchPacket(String connId, Packet packet) {
        switch (packet.getType()) {
            case REQUEST_CHUNK -> handleRequestChunk(connId, (RequestChunkPacket) packet);
            case COMBAT_RESULT -> handleCombatResult(connId, (CombatResultPacket) packet);
            case PLACE_TILE -> handlePlaceTile(connId, (PlaceTilePacket) packet);
            case PLAYER_CHAT -> handlePlayerChat(connId, (PlayerChatPacket) packet);
            case PERMISSION_ELEVATION -> handlePermissionElevation(connId, (PermissionElevationPacket) packet);
            case PLAYER_MOVE -> handlePlayerMove(connId, (PlayerMovePacket) packet);
            default -> LOGGER.warning("Unhandled packet type: " + packet.getType());
        }
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        long seed = System.currentTimeMillis();
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        if (args.length >= 2) seed = Long.parseLong(args[1]);

        GameServer server = new GameServer(port, seed);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.stop(); } catch (IOException ignored) {}
        }));
        server.start();
    }
}
