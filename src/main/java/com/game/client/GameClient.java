package com.game.client;

import com.game.client.renderer.*;
import com.game.client.scripting.ClientScriptEngine;
import com.game.client.ui.UIButton;
import com.game.client.ui.UITheme;
import com.game.common.entity.Player;
import com.game.common.network.NetworkSerializer;
import com.game.common.network.packets.*;
import com.game.common.world.Chunk;
import com.game.common.world.TileType;
import com.game.server.GameServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main game client.
 *
 * <p>Connects to the game server, renders the world using {@link GameRenderer},
 * and exposes the built-in scripting engine and world editor (God Mode) tool.
 */
public class GameClient extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(GameClient.class.getName());
    private static final long serialVersionUID = 1L;

    private static final int WINDOW_WIDTH  = 1280;
    private static final int WINDOW_HEIGHT = 800;
    private static final int TARGET_FPS    = 60;
    private static final long FRAME_DELAY_MS = 1000L / TARGET_FPS;

    // Networking
    private Socket socket;
    private NetworkSerializer serializer;
    private Thread networkThread;
    private volatile boolean connected = false;

    // World / player state
    private Player localPlayer;
    private volatile boolean godMode = false;
    private TileType godModeTile = TileType.GRASS;

    // Rendering
    private final UITheme theme;
    private final Camera camera;
    private final LightingSystem lighting;
    private final GameRenderer renderer;

    // Scripting
    private ClientScriptEngine scriptEngine;

    // UI
    private JLabel statusLabel;
    private JTextArea scriptInput;
    private JPanel sidebar;

    // Render timer
    private Timer renderTimer;

    public GameClient() {
        super("Infinite World – Game Client");
        this.theme   = new UITheme();
        this.camera  = new Camera(WINDOW_WIDTH, WINDOW_HEIGHT);
        this.lighting = new LightingSystem();
        this.renderer = new GameRenderer(camera, lighting, theme);
        buildUI();
        setupScriptEngine();
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(theme.backgroundColor());

        // --- Renderer (centre) ---
        renderer.setPreferredSize(new Dimension(WINDOW_WIDTH - 260, WINDOW_HEIGHT));
        add(renderer, BorderLayout.CENTER);

        // --- Sidebar ---
        sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(260, WINDOW_HEIGHT));
        sidebar.setBackground(theme.panelColor());
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Infinite World Engine");
        title.setFont(theme.boldFont().deriveFont(16f));
        title.setForeground(theme.textColor());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(10));

        // Connection button
        UIButton connectBtn = new UIButton("Connect to Server", theme);
        connectBtn.setIconChar("⚡");
        connectBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        connectBtn.setMaximumSize(new Dimension(240, 36));
        connectBtn.addActionListener(e -> connectToServer("127.0.0.1", GameServer.DEFAULT_PORT));
        sidebar.add(connectBtn);
        sidebar.add(Box.createVerticalStrut(6));

        // God Mode toggle
        UIButton godBtn = new UIButton("Toggle God Mode", theme);
        godBtn.setIconChar("👑");
        godBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        godBtn.setMaximumSize(new Dimension(240, 36));
        godBtn.addActionListener(e -> {
            godMode = !godMode;
            statusLabel.setText(godMode ? "God Mode: ON" : "God Mode: OFF");
        });
        sidebar.add(godBtn);
        sidebar.add(Box.createVerticalStrut(6));

        // Reload theme
        UIButton themeBtn = new UIButton("Reload Theme", theme);
        themeBtn.setIconChar("🎨");
        themeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        themeBtn.setMaximumSize(new Dimension(240, 36));
        themeBtn.addActionListener(e -> {
            theme.reload();
            renderer.repaint();
        });
        sidebar.add(themeBtn);
        sidebar.add(Box.createVerticalStrut(10));

        // Script input
        JLabel scriptLabel = new JLabel("Script Console (JS)");
        scriptLabel.setFont(theme.boldFont());
        scriptLabel.setForeground(theme.textColor());
        scriptLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(scriptLabel);
        sidebar.add(Box.createVerticalStrut(4));

        scriptInput = new JTextArea(6, 20);
        scriptInput.setBackground(theme.accentColor());
        scriptInput.setForeground(theme.textColor());
        scriptInput.setCaretColor(theme.textColor());
        scriptInput.setFont(new Font("Monospaced", Font.PLAIN, 11));
        scriptInput.setText("// Example: auto-heal\n"
                + "if (game.getHpPercent() < 0.2) {\n"
                + "    game.useItem('Healing_Potion');\n"
                + "}\n");
        JScrollPane scriptScroll = new JScrollPane(scriptInput);
        scriptScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scriptScroll.setMaximumSize(new Dimension(240, 130));
        sidebar.add(scriptScroll);
        sidebar.add(Box.createVerticalStrut(4));

        UIButton runScriptBtn = new UIButton("Run Script", theme);
        runScriptBtn.setIconChar("▶");
        runScriptBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        runScriptBtn.setMaximumSize(new Dimension(240, 36));
        runScriptBtn.addActionListener(e -> runScript());
        sidebar.add(runScriptBtn);
        sidebar.add(Box.createVerticalGlue());

        // Status bar
        statusLabel = new JLabel("Not connected");
        statusLabel.setFont(theme.defaultFont());
        statusLabel.setForeground(theme.textColor());
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(statusLabel);

        add(sidebar, BorderLayout.EAST);

        // --- Input handling ---
        renderer.setFocusable(true);
        renderer.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) { handleKey(e); }
        });
        renderer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) { handleMouseClick(e); }
        });
        renderer.addMouseWheelListener(e ->
                camera.setZoom(camera.getZoom() - e.getPreciseWheelRotation() * 0.1));

        // --- Render timer ---
        renderTimer = new Timer((int) FRAME_DELAY_MS, e -> {
            lighting.tick();
            renderer.tickParticles();
            updateHUD();
            renderer.repaint();
        });
        renderTimer.start();

        pack();
        setLocationRelativeTo(null);
        setResizable(true);
    }

    private void updateHUD() {
        String time = lighting.getTimeOfDay();
        String hp = localPlayer != null
                ? String.format("HP: %.0f", localPlayer.getHp()) : "HP: --";
        String pos = localPlayer != null
                ? String.format("Pos: (%.1f, %.1f)", localPlayer.getX(), localPlayer.getY()) : "";
        String god = godMode ? "\n👑 GOD MODE" : "";
        renderer.setHudText(time + "\n" + hp + "\n" + pos + god);
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    private void handleKey(KeyEvent e) {
        double speed = 0.5;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_UP    -> movePlayer(0, -speed);
            case KeyEvent.VK_S, KeyEvent.VK_DOWN  -> movePlayer(0, speed);
            case KeyEvent.VK_A, KeyEvent.VK_LEFT  -> movePlayer(-speed, 0);
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> movePlayer(speed, 0);
            case KeyEvent.VK_PLUS, KeyEvent.VK_EQUALS -> camera.setZoom(camera.getZoom() + 0.1);
            case KeyEvent.VK_MINUS                -> camera.setZoom(camera.getZoom() - 0.1);
            case KeyEvent.VK_G -> {
                godMode = !godMode;
                statusLabel.setText(godMode ? "God Mode: ON" : "God Mode: OFF");
            }
        }
    }

    private void movePlayer(double dx, double dy) {
        if (localPlayer == null) return;
        localPlayer.setX(localPlayer.getX() + dx);
        localPlayer.setY(localPlayer.getY() + dy);
        camera.centerOn(localPlayer.getX(), localPlayer.getY());
        sendPacket(new PlayerMovePacket(localPlayer.getX(), localPlayer.getY()));
        requestNearbyChunks();
    }

    private void handleMouseClick(MouseEvent e) {
        if (!godMode || !connected) return;
        double worldX = camera.screenToWorldX(e.getX());
        double worldY = camera.screenToWorldY(e.getY());
        int tx = (int) Math.floor(worldX);
        int ty = (int) Math.floor(worldY);
        // God Mode world editor: place a tile
        sendPacket(new PlaceTilePacket(tx, ty, godModeTile));
    }

    // -------------------------------------------------------------------------
    // Networking
    // -------------------------------------------------------------------------

    private void connectToServer(String host, int port) {
        statusLabel.setText("Connecting to " + host + ":" + port + "…");
        CompletableFuture.runAsync(() -> {
            try {
                socket = new Socket(host, port);
                serializer = new NetworkSerializer();
                connected = true;
                localPlayer = new Player("Player", 0, 0);
                renderer.addEntity(localPlayer);
                camera.centerOn(0, 0);
                requestNearbyChunks();
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Connected to " + host + ":" + port));
                startNetworkReceiver();
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Connection failed: " + ex.getMessage()));
                LOGGER.warning("Connect failed: " + ex.getMessage());
            }
        });
    }

    private void startNetworkReceiver() {
        networkThread = new Thread(() -> {
            try {
                InputStream in = socket.getInputStream();
                while (connected && !socket.isClosed()) {
                    com.game.common.network.packets.Packet pkt = serializer.readPacket(in);
                    SwingUtilities.invokeLater(() -> handleIncomingPacket(pkt));
                }
            } catch (IOException e) {
                LOGGER.fine("Network receiver stopped: " + e.getMessage());
            } finally {
                connected = false;
                SwingUtilities.invokeLater(() -> statusLabel.setText("Disconnected"));
            }
        }, "client-network");
        networkThread.setDaemon(true);
        networkThread.start();
    }

    private void handleIncomingPacket(com.game.common.network.packets.Packet pkt) {
        if (pkt instanceof ChunkDataPacket cdp) {
            Chunk chunk = cdp.getChunk();
            renderer.addChunk(chunk);

            // Add a torch light in ruins chunks for atmosphere
            if (chunk.getTile(32, 32).getBiome() == com.game.common.world.Biome.RUINS) {
                lighting.addLight(new LightingSystem.LightSource(
                        chunk.getWorldX() + 32, chunk.getWorldY() + 32, 8f, 0.9f));
            }
        }
    }

    private void sendPacket(com.game.common.network.packets.Packet packet) {
        if (!connected || socket == null || socket.isClosed()) return;
        try {
            serializer.writePacket(socket.getOutputStream(), packet);
        } catch (IOException e) {
            LOGGER.warning("Send error: " + e.getMessage());
        }
    }

    private void requestNearbyChunks() {
        if (!connected || localPlayer == null) return;
        int px = (int) Math.floor(localPlayer.getX());
        int py = (int) Math.floor(localPlayer.getY());
        int chunkX = Math.floorDiv(px, Chunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(py, Chunk.CHUNK_SIZE);
        int radius = 3;
        for (int cx = chunkX - radius; cx <= chunkX + radius; cx++) {
            for (int cy = chunkY - radius; cy <= chunkY + radius; cy++) {
                sendPacket(new RequestChunkPacket(cx, cy));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scripting
    // -------------------------------------------------------------------------

    private void setupScriptEngine() {
        ClientScriptEngine.GameScriptApi api = new ClientScriptEngine.GameScriptApi() {
            @Override public double getHpPercent() {
                if (localPlayer == null) return 1.0;
                double max = localPlayer.getStats()
                        .getFinalValue(com.game.common.stats.StatType.MAX_HP);
                return localPlayer.getHp() / max;
            }
            @Override public void useItem(String itemId) {
                sendPacket(new UseItemPacket(itemId, null));
            }
            @Override public void moveTo(double x, double y) { movePlayer(x, y); }
            @Override public void chat(String message) {
                if (localPlayer != null)
                    sendPacket(new PlayerChatPacket(localPlayer.getId(), message));
            }
            @Override public double getX() { return localPlayer != null ? localPlayer.getX() : 0; }
            @Override public double getY() { return localPlayer != null ? localPlayer.getY() : 0; }
            @Override public long getTick() { return System.currentTimeMillis() / 50; }
        };
        scriptEngine = new ClientScriptEngine(api);
        if (!scriptEngine.isAvailable()) {
            LOGGER.warning("JavaScript engine unavailable; scripting disabled.");
        }
    }

    private void runScript() {
        if (scriptEngine == null || !scriptEngine.isAvailable()) {
            statusLabel.setText("Scripting engine not available");
            return;
        }
        String code = scriptInput.getText();
        Object result = scriptEngine.eval(code);
        statusLabel.setText("Script result: " + result);
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameClient client = new GameClient();
            client.setVisible(true);
        });
    }
}
