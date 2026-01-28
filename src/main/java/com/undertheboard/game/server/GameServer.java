package com.undertheboard.game.server;

import com.undertheboard.game.common.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Game server that manages multiplayer game state and handles client connections.
 */
public class GameServer {
    private static final int GAME_PORT = 9876;
    private static final int DISCOVERY_PORT = 9875;
    private static final int MAX_PLAYERS = 10;
    private static final int TICK_RATE = 30; // 30 updates per second
    private static final float FIELD_WIDTH = 800;
    private static final float FIELD_HEIGHT = 600;
    
    private GameState gameState;
    private Map<String, ClientHandler> clients;
    private ServerSocket serverSocket;
    private DatagramSocket discoverySocket;
    private boolean running;
    private String serverName;
    
    public GameServer(String serverName) {
        this.serverName = serverName;
        this.gameState = new GameState();
        this.clients = new ConcurrentHashMap<>();
        this.running = false;
    }
    
    public void start() throws IOException {
        serverSocket = new ServerSocket(GAME_PORT);
        discoverySocket = new DatagramSocket(DISCOVERY_PORT);
        running = true;
        
        System.out.println("Game Server started on port " + GAME_PORT);
        System.out.println("Discovery service on port " + DISCOVERY_PORT);
        
        // Start discovery broadcaster
        new Thread(this::runDiscoveryBroadcaster).start();
        
        // Start game loop
        new Thread(this::runGameLoop).start();
        
        // Accept client connections
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void runDiscoveryBroadcaster() {
        while (running) {
            try {
                // Listen for discovery requests
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                discoverySocket.setSoTimeout(1000);
                
                try {
                    discoverySocket.receive(packet);
                    
                    // Send server information back
                    ServerAnnounceMessage announce = new ServerAnnounceMessage(
                        serverName,
                        clients.size(),
                        MAX_PLAYERS,
                        GAME_PORT
                    );
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(announce);
                    oos.flush();
                    
                    byte[] responseData = baos.toByteArray();
                    DatagramPacket response = new DatagramPacket(
                        responseData,
                        responseData.length,
                        packet.getAddress(),
                        packet.getPort()
                    );
                    discoverySocket.send(response);
                    
                    System.out.println("Responded to discovery request from " + packet.getAddress());
                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue
                }
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void runGameLoop() {
        long lastUpdate = System.currentTimeMillis();
        long tickInterval = 1000 / TICK_RATE;
        
        while (running) {
            long now = System.currentTimeMillis();
            long delta = now - lastUpdate;
            
            if (delta >= tickInterval) {
                gameState.update();
                broadcastGameState();
                lastUpdate = now;
            }
            
            try {
                Thread.sleep(Math.max(1, tickInterval - delta));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void broadcastGameState() {
        GameStateMessage message = new GameStateMessage(gameState);
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }
    
    private class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String playerId;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
                
                while (running && !socket.isClosed()) {
                    try {
                        NetworkMessage message = (NetworkMessage) in.readObject();
                        handleMessage(message);
                    } catch (EOFException | SocketException e) {
                        // Client disconnected
                        break;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
        }
        
        private void handleMessage(NetworkMessage message) {
            switch (message.getType()) {
                case PLAYER_JOIN:
                    PlayerJoinMessage joinMsg = (PlayerJoinMessage) message;
                    handlePlayerJoin(joinMsg);
                    break;
                    
                case PLAYER_MOVE:
                    PlayerMoveMessage moveMsg = (PlayerMoveMessage) message;
                    handlePlayerMove(moveMsg);
                    break;
                    
                default:
                    System.out.println("Unknown message type: " + message.getType());
            }
        }
        
        private void handlePlayerJoin(PlayerJoinMessage message) {
            playerId = UUID.randomUUID().toString();
            
            // Spawn player at random position
            float x = (float) (Math.random() * (FIELD_WIDTH - 100) + 50);
            float y = (float) (Math.random() * (FIELD_HEIGHT - 100) + 50);
            
            PlayerModel player = new PlayerModel(playerId, message.getPlayerName(), x, y);
            gameState.addPlayer(player);
            clients.put(playerId, this);
            
            System.out.println("Player joined: " + message.getPlayerName() + " (ID: " + playerId + ")");
            
            // Send player ID to the client first
            sendMessage(new PlayerIdMessage(playerId));
            
            // Then send initial game state to the new player
            sendMessage(new GameStateMessage(gameState));
        }
        
        private void handlePlayerMove(PlayerMoveMessage message) {
            if (playerId != null && playerId.equals(message.getPlayerId())) {
                // Clamp to field boundaries
                float clampedX = Math.max(10, Math.min(FIELD_WIDTH - 10, message.getTargetX()));
                float clampedY = Math.max(10, Math.min(FIELD_HEIGHT - 10, message.getTargetY()));
                
                gameState.updatePlayer(playerId, clampedX, clampedY);
            }
        }
        
        public void sendMessage(NetworkMessage message) {
            try {
                out.writeObject(message);
                out.flush();
                out.reset(); // Prevent caching issues
            } catch (IOException e) {
                // Client likely disconnected
            }
        }
        
        private void cleanup() {
            if (playerId != null) {
                gameState.removePlayer(playerId);
                clients.remove(playerId);
                System.out.println("Player disconnected: " + playerId);
            }
            
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (discoverySocket != null) discoverySocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        String serverName = args.length > 0 ? args[0] : "Game Server";
        GameServer server = new GameServer(serverName);
        
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
