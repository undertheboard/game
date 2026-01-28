package com.undertheboard.game.client;

import com.undertheboard.game.common.PlayerModel;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renderer for player models in the game.
 */
public class PlayerRenderer {
    private static final float SIZE = 20.0f;
    
    public static void render(PlayerModel player) {
        glPushMatrix();
        glTranslatef(player.getX(), player.getY(), 0);
        
        // Draw player as a colored square
        glColor3f(player.getColorR(), player.getColorG(), player.getColorB());
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
        
        // Draw player name above the player
        glPopMatrix();
        
        // Draw name tag
        renderNameTag(player);
    }
    
    private static void renderNameTag(PlayerModel player) {
        // Simple name tag background
        float nameWidth = player.getName().length() * 6;
        float nameHeight = 12;
        float nameX = player.getX() - nameWidth / 2;
        float nameY = player.getY() - SIZE / 2 - 15;
        
        glColor4f(0.0f, 0.0f, 0.0f, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(nameX - 2, nameY - 2);
        glVertex2f(nameX + nameWidth + 2, nameY - 2);
        glVertex2f(nameX + nameWidth + 2, nameY + nameHeight + 2);
        glVertex2f(nameX - 2, nameY + nameHeight + 2);
        glEnd();
        
        // Note: Actual text rendering would require a font system
        // For now, we just show the colored background
    }
}
