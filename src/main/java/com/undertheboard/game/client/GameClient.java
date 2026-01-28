package com.undertheboard.game.client;

import com.undertheboard.game.common.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network client for connecting to the game server.
 */
public class GameClient {
    private static final int DISCOVERY_PORT = 9875;
    private static final int DISCOVERY_TIMEOUT = 3000;
    
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String playerId;
    private GameState gameState;
    private boolean connected;
    private Thread receiveThread;
    private Map<String, ServerInfo> discoveredServers;
    
    public GameClient() {
        this.gameState = new GameState();
        this.connected = false;
        this.discoveredServers = new ConcurrentHashMap<>();
    }
    
    /**
     * Discover servers on the local network.
     */
    public List<ServerInfo> discoverServers() {
        discoveredServers.clear();
        
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(DISCOVERY_TIMEOUT);
            
            // Send discovery request
            byte[] requestData = new byte[1];
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            DatagramPacket request = new DatagramPacket(
                requestData,
                requestData.length,
                broadcastAddr,
                DISCOVERY_PORT
            );
            socket.send(request);
            
            System.out.println("Sent discovery request...");
            
            // Receive responses
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT) {
                try {
                    byte[] buffer = new byte[4096];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    // Deserialize server announcement
                    ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData());
                    ObjectInputStream ois = new ObjectInputStream(bais);
                    ServerAnnounceMessage announce = (ServerAnnounceMessage) ois.readObject();
                    
                    ServerInfo info = new ServerInfo(
                        announce.getServerName(),
                        packet.getAddress().getHostAddress(),
                        announce.getPort(),
                        announce.getPlayerCount(),
                        announce.getMaxPlayers()
                    );
                    
                    discoveredServers.put(info.getAddress(), info);
                    System.out.println("Discovered server: " + info);
                    
                } catch (SocketTimeoutException e) {
                    // Timeout waiting for response
                    break;
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return new ArrayList<>(discoveredServers.values());
    }
    
    /**
     * Connect to a game server.
     */
    public boolean connect(String host, int port, String playerName) {
        // Validate player name
        if (playerName == null || playerName.trim().isEmpty()) {
            playerName = "Player";
        }
        playerName = playerName.trim();
        
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            connected = true;
            
            // Send join message
            PlayerJoinMessage joinMsg = new PlayerJoinMessage(playerName);
            sendMessage(joinMsg);
            
            // Start receive thread
            receiveThread = new Thread(this::receiveLoop);
            receiveThread.start();
            
            System.out.println("Connected to server at " + host + ":" + port);
            return true;
            
        } catch (IOException e) {
            System.err.println("Failed to connect: " + e.getMessage());
            return false;
        }
    }
    
    private void receiveLoop() {
        while (connected && !socket.isClosed()) {
            try {
                NetworkMessage message = (NetworkMessage) in.readObject();
                handleMessage(message);
            } catch (EOFException | SocketException e) {
                // Server disconnected
                connected = false;
                break;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                connected = false;
                break;
            }
        }
    }
    
    private void handleMessage(NetworkMessage message) {
        if (message.getType() == NetworkMessage.MessageType.GAME_STATE) {
            GameStateMessage stateMsg = (GameStateMessage) message;
            this.gameState = stateMsg.getGameState();
        } else if (message.getType() == NetworkMessage.MessageType.PLAYER_JOIN) {
            // Receive player ID from server
            PlayerIdMessage idMsg = (PlayerIdMessage) message;
            this.playerId = idMsg.getPlayerId();
            System.out.println("Assigned player ID: " + playerId);
        }
    }
    
    /**
     * Send a move command to the server.
     */
    public void sendMove(float targetX, float targetY) {
        if (connected && playerId != null) {
            PlayerMoveMessage moveMsg = new PlayerMoveMessage(playerId, targetX, targetY);
            sendMessage(moveMsg);
        }
    }
    
    private void sendMessage(NetworkMessage message) {
        try {
            out.writeObject(message);
            out.flush();
            out.reset();
        } catch (IOException e) {
            e.printStackTrace();
            connected = false;
        }
    }
    
    public GameState getGameState() {
        return gameState;
    }
    
    public String getPlayerId() {
        return playerId;
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public void disconnect() {
        connected = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            if (receiveThread != null) receiveThread.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Information about a discovered server.
     */
    public static class ServerInfo {
        private String name;
        private String address;
        private int port;
        private int playerCount;
        private int maxPlayers;
        
        public ServerInfo(String name, String address, int port, int playerCount, int maxPlayers) {
            this.name = name;
            this.address = address;
            this.port = port;
            this.playerCount = playerCount;
            this.maxPlayers = maxPlayers;
        }
        
        public String getName() { return name; }
        public String getAddress() { return address; }
        public int getPort() { return port; }
        public int getPlayerCount() { return playerCount; }
        public int getMaxPlayers() { return maxPlayers; }
        
        @Override
        public String toString() {
            return name + " (" + address + ":" + port + ") - " + playerCount + "/" + maxPlayers + " players";
        }
    }
}
