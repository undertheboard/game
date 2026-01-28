package com.undertheboard.game;

import static org.lwjgl.opengl.GL11.*;

public class Player {
    private float x;
    private float y;
    private float targetX;
    private float targetY;
    private static final float SIZE = 20.0f;
    
    // Field boundaries
    private static final float MIN_X = SIZE / 2;
    private static final float MAX_X = 800 - SIZE / 2;
    private static final float MIN_Y = SIZE / 2;
    private static final float MAX_Y = 600 - SIZE / 2;

    public Player(float x, float y) {
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
    }

    public void move(float dx, float dy) {
        targetX += dx;
        targetY += dy;
        
        // Clamp to field boundaries
        targetX = Math.max(MIN_X, Math.min(MAX_X, targetX));
        targetY = Math.max(MIN_Y, Math.min(MAX_Y, targetY));
    }

    public void update() {
        // Smooth movement towards target
        float lerpFactor = 0.3f;
        x += (targetX - x) * lerpFactor;
        y += (targetY - y) * lerpFactor;
    }

    public void render() {
        glPushMatrix();
        glTranslatef(x, y, 0);
        
        // Draw player as a red square
        glColor3f(1.0f, 0.2f, 0.2f);
        glBegin(GL_QUADS);
        glVertex2f(-SIZE / 2, -SIZE / 2);
        glVertex2f(SIZE / 2, -SIZE / 2);
        glVertex2f(SIZE / 2, SIZE / 2);
        glVertex2f(-SIZE / 2, SIZE / 2);
        glEnd();
        
        // Draw player direction indicator (small triangle)
        glColor3f(1.0f, 1.0f, 1.0f);
        glBegin(GL_TRIANGLES);
        glVertex2f(0, -SIZE / 3);
        glVertex2f(-SIZE / 4, SIZE / 6);
        glVertex2f(SIZE / 4, SIZE / 6);
        glEnd();
        
        glPopMatrix();
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }
}
