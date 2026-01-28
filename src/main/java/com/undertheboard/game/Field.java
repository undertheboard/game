package com.undertheboard.game;

import static org.lwjgl.opengl.GL11.*;

public class Field {
    private int width;
    private int height;
    private static final int TILE_SIZE = 40;

    public Field(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void render() {
        // Draw a checkerboard pattern for the field
        int tilesX = width / TILE_SIZE;
        int tilesY = height / TILE_SIZE;

        for (int y = 0; y < tilesY; y++) {
            for (int x = 0; x < tilesX; x++) {
                float posX = x * TILE_SIZE;
                float posY = y * TILE_SIZE;

                // Alternate colors for checkerboard pattern
                if ((x + y) % 2 == 0) {
                    glColor3f(0.3f, 0.6f, 0.3f); // Green
                } else {
                    glColor3f(0.25f, 0.5f, 0.25f); // Darker green
                }

                glBegin(GL_QUADS);
                glVertex2f(posX, posY);
                glVertex2f(posX + TILE_SIZE, posY);
                glVertex2f(posX + TILE_SIZE, posY + TILE_SIZE);
                glVertex2f(posX, posY + TILE_SIZE);
                glEnd();

                // Draw grid lines
                glColor3f(0.2f, 0.4f, 0.2f);
                glBegin(GL_LINE_LOOP);
                glVertex2f(posX, posY);
                glVertex2f(posX + TILE_SIZE, posY);
                glVertex2f(posX + TILE_SIZE, posY + TILE_SIZE);
                glVertex2f(posX, posY + TILE_SIZE);
                glEnd();
            }
        }
    }
}
