# LWJGL Walkable Field Game

A simple 2D game using LWJGL (Lightweight Java Game Library) where a player can walk on a field in all directions.

## Features

- Walk on a green checkered field
- Movement in all directions using WASD or arrow keys
- Smooth player movement
- Player boundaries (cannot walk off the field)

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

This will create a JAR file in the `target/` directory.

## Running

To run the game directly:

```bash
mvn exec:java -Dexec.mainClass="com.undertheboard.game.Game"
```

Or run the compiled JAR:

```bash
java -jar target/game-1.0.0.jar
```

## Requirements

- Java 11 or higher
- Maven 3.6 or higher
- **Platform**: The compiled JAR includes native libraries for Linux. To run on Windows or macOS, you need to rebuild the project on that platform (Maven will automatically download the correct native libraries for your system).

## Description

The game features:
- A player represented by a red square with a white directional indicator
- A green checkered field that represents the walkable area
- Smooth movement system with boundaries to keep the player on the field
- Real-time keyboard input handling for responsive controls