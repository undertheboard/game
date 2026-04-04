package com.game.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Custom "Skinable" button that draws itself using the active {@link UITheme}.
 * Supports hover highlighting, rounded corners, and an optional icon character.
 */
public class UIButton extends JButton {
    private static final long serialVersionUID = 1L;

    private UITheme theme;
    private boolean hovered = false;
    private String iconChar = "";

    public UIButton(String text, UITheme theme) {
        super(text);
        this.theme = theme;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
        });
    }

    public void setTheme(UITheme theme) {
        this.theme = theme;
        repaint();
    }

    public void setIconChar(String iconChar) {
        this.iconChar = iconChar;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = 10;

        // Background
        g2.setColor(hovered ? theme.buttonHoverColor() : theme.buttonColor());
        g2.fillRoundRect(0, 0, w, h, arc, arc);

        // Subtle border
        g2.setColor(theme.accentColor());
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

        // Icon
        if (!iconChar.isEmpty()) {
            g2.setFont(new Font(theme.fontFamily(), Font.BOLD, theme.fontSize() + 2));
            g2.setColor(theme.textColor());
            FontMetrics fm = g2.getFontMetrics();
            int ix = (h - fm.getHeight()) / 2 + fm.getAscent() - 2;
            g2.drawString(iconChar, 8, ix);
        }

        // Label
        g2.setFont(theme.boldFont());
        g2.setColor(theme.textColor());
        FontMetrics fm = g2.getFontMetrics();
        String label = getText();
        int tx = iconChar.isEmpty()
                ? (w - fm.stringWidth(label)) / 2
                : 8 + fm.stringWidth(iconChar) + 6;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(label, tx, ty);

        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(theme.boldFont());
        int w = fm.stringWidth(getText()) + (iconChar.isEmpty() ? 32 : 52);
        int h = theme.fontSize() + 20;
        return new Dimension(w, h);
    }
}
