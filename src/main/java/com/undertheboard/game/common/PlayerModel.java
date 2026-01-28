package com.undertheboard.game.common;

import java.io.Serializable;

/**
 * Represents a player in the game with position, movement, and visual properties.
 */
public class PlayerModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String name;
    private float x;
    private float y;
    private float targetX;
    private float targetY;
    private float colorR;
    private float colorG;
    private float colorB;
    private long lastUpdate;
    
    public PlayerModel(String id, String name, float x, float y) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
        this.lastUpdate = System.currentTimeMillis();
        
        // Assign a random color to the player
        this.colorR = (float) Math.random();
        this.colorG = (float) Math.random();
        this.colorB = (float) Math.random();
        
        // Ensure color is bright enough
        float brightness = colorR + colorG + colorB;
        if (brightness < 1.0f) {
            float scale = 1.5f / brightness;
            colorR = Math.min(1.0f, colorR * scale);
            colorG = Math.min(1.0f, colorG * scale);
            colorB = Math.min(1.0f, colorB * scale);
        }
    }
    
    public void setTarget(float targetX, float targetY) {
        this.targetX = targetX;
        this.targetY = targetY;
        this.lastUpdate = System.currentTimeMillis();
    }
    
    public void update() {
        // Smooth movement towards target
        float lerpFactor = 0.3f;
        x += (targetX - x) * lerpFactor;
        y += (targetY - y) * lerpFactor;
    }
    
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
        this.lastUpdate = System.currentTimeMillis();
    }
    
    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getTargetX() { return targetX; }
    public float getTargetY() { return targetY; }
    public float getColorR() { return colorR; }
    public float getColorG() { return colorG; }
    public float getColorB() { return colorB; }
    public long getLastUpdate() { return lastUpdate; }
    
    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public void setTargetX(float targetX) { this.targetX = targetX; }
    public void setTargetY(float targetY) { this.targetY = targetY; }
}
