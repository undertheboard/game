package com.undertheboard.game;

import com.undertheboard.game.client.GameClient;
import com.undertheboard.game.client.PlayerRenderer;
import com.undertheboard.game.common.PlayerModel;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;
import java.util.List;
import java.util.Scanner;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Game {
    private long window;
    private Field field;
    private GameClient client;
    private PlayerModel localPlayer;

    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;

    public void run() {
        // Connect to server first
        if (!connectToServer()) {
            System.err.println("Failed to connect to server. Exiting.");
            return;
        }
        
        init();
        loop();

        // Cleanup
        client.disconnect();
        
        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
    
    private boolean connectToServer() {
        client = new GameClient();
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=== Game Client ===");
        System.out.println("Discovering servers on local network...");
        
        List<GameClient.ServerInfo> servers = client.discoverServers();
        
        if (servers.isEmpty()) {
            System.out.println("No servers found. Enter server address manually? (y/n)");
            String response = scanner.nextLine().trim().toLowerCase();
            
            if (response.equals("y")) {
                System.out.print("Server address (default: localhost): ");
                String host = scanner.nextLine().trim();
                if (host.isEmpty()) host = "localhost";
                
                System.out.print("Player name: ");
                String playerName = scanner.nextLine().trim();
                if (playerName.isEmpty()) playerName = "Player";
                
                return client.connect(host, 9876, playerName);
            } else {
                return false;
            }
        } else {
            System.out.println("\nDiscovered servers:");
            for (int i = 0; i < servers.size(); i++) {
                System.out.println((i + 1) + ". " + servers.get(i));
            }
            
            System.out.print("\nSelect server (1-" + servers.size() + "): ");
            int choice = 0;
            try {
                choice = Integer.parseInt(scanner.nextLine().trim()) - 1;
            } catch (NumberFormatException e) {
                choice = 0;
            }
            
            if (choice < 0 || choice >= servers.size()) {
                choice = 0;
            }
            
            GameClient.ServerInfo server = servers.get(choice);
            
            System.out.print("Player name: ");
            String playerName = scanner.nextLine().trim();
            if (playerName.isEmpty()) playerName = "Player";
            
            return client.connect(server.getAddress(), server.getPort(), playerName);
        }
    }

    private void init() {
        // Setup an error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        // Create the window
        window = glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Multiplayer Field Game", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Setup key callback
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }
        });

        // Get the thread stack and push a new frame
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            // Get the window size
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth.get(0)) / 2,
                (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);

        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        GL.createCapabilities();

        // Initialize game objects
        field = new Field(WINDOW_WIDTH, WINDOW_HEIGHT);

        // Set up the viewport
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, WINDOW_WIDTH, WINDOW_HEIGHT, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
    }

    private void loop() {
        // Set the clear color
        glClearColor(0.2f, 0.3f, 0.3f, 1.0f);

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key
        while (!glfwWindowShouldClose(window) && client.isConnected()) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Handle input
            handleInput();

            // Render
            field.render();
            
            // Render all players
            for (PlayerModel player : client.getGameState().getPlayers().values()) {
                PlayerRenderer.render(player);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void handleInput() {
        // Get local player
        if (client.getPlayerId() == null) return;
        
        localPlayer = client.getGameState().getPlayer(client.getPlayerId());
        if (localPlayer == null) return;
        
        float speed = 5.0f;
        float newTargetX = localPlayer.getTargetX();
        float newTargetY = localPlayer.getTargetY();
        boolean moved = false;
        
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) {
            newTargetY -= speed;
            moved = true;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) {
            newTargetY += speed;
            moved = true;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) {
            newTargetX -= speed;
            moved = true;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) {
            newTargetX += speed;
            moved = true;
        }
        
        if (moved) {
            client.sendMove(newTargetX, newTargetY);
        }
    }

    public static void main(String[] args) {
        new Game().run();
    }
}
