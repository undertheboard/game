package com.redistricting.gui;

import com.redistricting.ai.FairnessAnalyzer;
import com.redistricting.ai.FairnessReport;
import com.redistricting.ai.GenerationParams;
import com.redistricting.ai.MapGenerator;
import com.redistricting.io.DraImporter;
import com.redistricting.io.DraGeoJsonLoader;
import com.redistricting.model.RedistrictingMap;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Main application window. The toolbar / menu offers a single
 * <strong>Import from Dave's Redistricting</strong> action that auto-detects
 * the file type, plus an AI menu to analyze and (when precinct data is
 * available) optimize fairness.
 */
public final class MainFrame extends JFrame {

    private final MapPanel mapPanel = new MapPanel();
    private final JLabel statusBar = new JLabel(" Ready");
    private final FairnessAnalyzer analyzer = new FairnessAnalyzer();

    public MainFrame() {
        super("Redistricting Fairness Analyzer (Dave's Redistricting compatible)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(buildMenuBar());

        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(mapPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        setSize(new Dimension(1000, 720));
        setLocationRelativeTo(null);

        loadBundledSample();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem importDra = new JMenuItem("Import from Dave's Redistricting…");
        importDra.addActionListener(e -> doImport());
        JMenuItem reset = new JMenuItem("Reset View");
        reset.addActionListener(e -> { mapPanel.resetView(); mapPanel.repaint(); });
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());
        fileMenu.add(importDra);
        fileMenu.addSeparator();
        fileMenu.add(reset);
        fileMenu.addSeparator();
        fileMenu.add(exit);

        JMenu aiMenu = new JMenu("AI");
        JMenuItem analyze = new JMenuItem("Analyze Fairness");
        analyze.addActionListener(e -> doAnalyze());
        JMenuItem optimize = new JMenuItem("Optimize Fairness");
        optimize.addActionListener(e -> doOptimize());
        JMenuItem generate = new JMenuItem("Generate Plan…");
        generate.addActionListener(e -> doGenerate());
        aiMenu.add(generate);
        aiMenu.addSeparator();
        aiMenu.add(analyze);
        aiMenu.add(optimize);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem tutorial = new JMenuItem("Tutorial");
        tutorial.addActionListener(e -> TutorialDialog.show(this));
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Redistricting Fairness Analyzer\n\n"
                + "Imports plans exported from Dave's Redistricting App\n"
                + "(District Shapes .geojson, District Data .csv, or Map Archive .json),\n"
                + "analyses fairness with population deviation, Polsby-Popper\n"
                + "compactness, and the efficiency gap, and can generate plans\n"
                + "with a tunable partisan bias.",
                "About", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(tutorial);
        helpMenu.addSeparator();
        helpMenu.add(about);

        bar.add(fileMenu);
        bar.add(aiMenu);
        bar.add(helpMenu);
        return bar;
    }

    private void doImport() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Import file exported from Dave's Redistricting");
        fc.setAcceptAllFileFilterUsed(true);
        fc.addChoosableFileFilter(new FileNameExtensionFilter(
                "Dave's Redistricting exports (*.geojson, *.json, *.csv)",
                "geojson", "json", "csv"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter(
                "District Shapes (*.geojson)", "geojson"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter(
                "Map Archive (*.json)", "json"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter(
                "District Data (*.csv)", "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = fc.getSelectedFile().toPath();
        try {
            DraImporter.Result result = DraImporter.importFile(path, mapPanel.getMap());
            mapPanel.setMap(result.map());
            statusBar.setText(" " + result.description());
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to import:\n" + ex.getMessage(),
                    "Import error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doAnalyze() {
        RedistrictingMap map = mapPanel.getMap();
        if (map == null) {
            JOptionPane.showMessageDialog(this, "Load a plan first.");
            return;
        }
        FairnessReport report = analyzer.analyze(map);
        showReport("Fairness Report", report.prettyPrint(map.name()));
    }

    private void doOptimize() {
        RedistrictingMap map = mapPanel.getMap();
        if (map == null) {
            JOptionPane.showMessageDialog(this, "Load a plan first.");
            return;
        }
        if (map.precincts().size() <= map.districtCount()) {
            JOptionPane.showMessageDialog(this,
                    "This plan has only one precinct per district\n"
                    + "(typical for DRA District Shapes exports).\n\n"
                    + "Load a precinct-level plan to enable the optimizer.",
                    "Optimizer unavailable", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        FairnessReport before = analyzer.analyze(map);
        FairnessReport after = analyzer.optimize(map, 5000, 42L);
        mapPanel.repaint();
        showReport("Optimization complete",
                "BEFORE\n" + before.prettyPrint(map.name())
                + "\nAFTER\n" + after.prettyPrint(map.name()));
    }

    private void showReport(String title, String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(620, 460));
        JOptionPane.showMessageDialog(this, scroll, title,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void doGenerate() {
        GenerationParams params = GenerateDialog.showDialog(this);
        if (params == null) return;
        statusBar.setText(" Generating plan… (" + params.attempts() + " attempts)");
        // Run on a background thread so the UI stays responsive.
        new Thread(() -> {
            try {
                RedistrictingMap map = new MapGenerator().generate(params);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    mapPanel.setMap(map);
                    statusBar.setText(" " + map.name()
                            + " — " + map.precincts().size() + " precincts, "
                            + map.districtCount() + " districts");
                });
            } catch (RuntimeException ex) {
                javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this, "Generation failed:\n" + ex.getMessage(),
                        "Generate", JOptionPane.ERROR_MESSAGE));
            }
        }, "plan-generator").start();
    }

    private void loadBundledSample() {
        try {
            RedistrictingMap sample = DraGeoJsonLoader.loadFromResource("sample-dra.geojson");
            mapPanel.setMap(sample);
            statusBar.setText(" Loaded bundled sample plan — use File → Import from"
                    + " Dave's Redistricting… to open your own export.");
        } catch (IOException | RuntimeException ex) {
            statusBar.setText(" No plan loaded (" + ex.getMessage() + ")");
        }
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            MainFrame f = new MainFrame();
            f.setVisible(true);
            TutorialDialog.showOnFirstLaunch(f);
        });
    }
}
