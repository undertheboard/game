package com.undertheboard.game.common;

/**
 * Message broadcast by server for discovery on local network.
 */
public class ServerAnnounceMessage extends NetworkMessage {
    private static final long serialVersionUID = 1L;
    
    private String serverName;
    private int playerCount;
    private int maxPlayers;
    private int port;
    
    public ServerAnnounceMessage(String serverName, int playerCount, int maxPlayers, int port) {
        super(MessageType.SERVER_ANNOUNCE);
        this.serverName = serverName;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.port = port;
    }
    
    public String getServerName() {
        return serverName;
    }
    
    public int getPlayerCount() {
        return playerCount;
    }
    
    public int getMaxPlayers() {
        return maxPlayers;
    }
    
    public int getPort() {
        return port;
    }
}
