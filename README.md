# LWJGL Multiplayer Field Game

A multiplayer 2D game using LWJGL (Lightweight Java Game Library) where players can walk on a field together. Features client-server architecture with automatic server discovery on the local network.

## Features

- **Multiplayer**: Multiple players can connect to the same server
- **Server Discovery**: Clients automatically detect servers on the local network
- **Player Models**: Each player has a unique color and name
- **Smooth Movement**: Players can move in all directions using WASD or arrow keys
- **Real-time Synchronization**: Game state is synchronized across all clients

## Architecture

The game consists of two main components:

1. **Game Server**: Hosts the game, manages player connections, and synchronizes game state
2. **Game Client**: Connects to a server and allows players to control their character

## Controls

- **W** or **↑**: Move up
- **S** or **↓**: Move down
- **A** or **←**: Move left
- **D** or **→**: Move right
- **ESC**: Exit the game

## Building

To build the project, run:

```bash
mvn clean package
```

This will create two JAR files in the `target/` directory:
- `game-server-1.0.0-server.jar` - The game server
- `game-client-1.0.0-client.jar` - The game client

## Running

### Starting the Server

To start a game server:

```bash
java -jar target/game-server-1.0.0-server.jar "My Server Name"
```

Or without a server name:

```bash
java -jar target/game-server-1.0.0-server.jar
```

The server will:
- Listen for client connections on port 9876
- Broadcast its presence on port 9875 for automatic discovery
- Display connection information in the console

### Starting the Client

To start a game client:

```bash
java -jar target/game-client-1.0.0-client.jar
```

The client will:
1. Automatically search for servers on the local network
2. Display a list of discovered servers
3. Prompt you to select a server and enter your player name
4. Connect to the server and start the game

If no servers are found, you can manually enter a server address.

## Requirements

- Java 11 or higher
- Maven 3.6 or higher
- **Platform**: The compiled client JAR includes native libraries for Linux. To run on Windows or macOS, you need to rebuild the project on that platform (Maven will automatically download the correct native libraries for your system).
- **Network**: For server discovery to work, clients and servers must be on the same local network and UDP broadcast must be allowed through firewalls.

## Network Ports

- **9876**: Game server (TCP) - Used for client-server communication
- **9875**: Discovery service (UDP) - Used for automatic server discovery

## Description

The game features:
- **Multiplayer gameplay**: Multiple players can join the same server and see each other moving
- **Player models**: Each player is represented by a colored square with unique colors
- **Green checkered field**: Visual representation of the walkable area
- **Smooth movement**: Interpolated movement for better visual feedback
- **Player boundaries**: Players are kept within the field boundaries by the server
- **Real-time updates**: Game state is synchronized 30 times per second
- **Automatic discovery**: Clients can automatically find servers on the local network
- **Name tags**: Each player displays their name above their character