package com.redistricting;

import com.redistricting.gui.MainFrame;

/** Application entry point — launches the Swing GUI on the EDT. */
public final class RedistrictingApp {
    private RedistrictingApp() {}

    public static void main(String[] args) {
        // Allow headless mode (smoke test the JAR in CI/Docker).
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("Redistricting Fairness Analyzer");
            System.out.println("(headless mode — start a desktop session to use the GUI)");
            return;
        }
        MainFrame.launch();
    }
}
