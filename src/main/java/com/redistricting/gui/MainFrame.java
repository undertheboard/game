package com.redistricting.gui;

import com.redistricting.ai.FairnessAnalyzer;
import com.redistricting.ai.FairnessReport;
import com.redistricting.ai.GenerationParams;
import com.redistricting.ai.MapGenerator;
import com.redistricting.ai.algorithms.PrecinctBase;
import com.redistricting.io.DraImporter;
import com.redistricting.io.PlanGeoJsonWriter;
import com.redistricting.io.Presets;
import com.redistricting.io.RdhPrecinctLoader;
import com.redistricting.model.RedistrictingMap;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application window.
 *
 * <p>The window is constructed with a known-good {@link RedistrictingMap}
 * loaded by {@link RdhStartupDialog}; the Import / Generate / View menus
 * are always enabled because the user has already chosen a base. All file
 * dialogs use the OS-native picker via {@link NativeFileChooser}.
 */
public final class MainFrame extends JFrame {

    private final MapPanel mapPanel = new MapPanel();
    private final JLabel statusBar = new JLabel(" Ready");
    private final FairnessAnalyzer analyzer = new FairnessAnalyzer();
    private final List<JMenuItem> mapDependentItems = new ArrayList<>();

    public MainFrame(RedistrictingMap initialMap) {
        super("Redistricting Fairness Analyzer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(buildMenuBar());

        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(mapPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        setSize(new Dimension(1100, 760));
        setLocationRelativeTo(null);

        // Always run with the dark theme; map panel needs to know so its
        // own custom-painted layers (legend, halos, etc.) match.
        mapPanel.setDarkMode(true);

        if (initialMap != null) {
            mapPanel.setMap(initialMap);
            statusBar.setText(" " + describe(initialMap));
        }
        refreshMapDependentItems();
    }

    // ---------- menu wiring ----------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(buildFileMenu());
        bar.add(buildViewMenu());
        bar.add(buildAiMenu());
        bar.add(buildHelpMenu());
        return bar;
    }

    private JMenu buildFileMenu() {
        JMenu fileMenu = new JMenu("File");

        JMenuItem importRdh = new JMenuItem("Import precinct data from Redistricting Data Hub…");
        importRdh.addActionListener(e -> doImportRdh());
        JMenu presetsMenu = new JMenu("Load bundled preset");
        for (Presets.Preset preset : Presets.all()) {
            JMenuItem mi = new JMenuItem(preset.displayName());
            mi.addActionListener(e -> doLoadPreset(preset));
            presetsMenu.add(mi);
        }
        JMenuItem importDra = new JMenuItem("Import plan / data from Dave's Redistricting…");
        importDra.addActionListener(e -> doImportDra());
        JMenuItem applyBef = new JMenuItem("Apply block-equivalency file (CSV BEF)…");
        applyBef.addActionListener(e -> doApplyBef());
        JMenuItem savePlan = new JMenuItem("Save current plan as GeoJSON…");
        savePlan.addActionListener(e -> doSavePlan());
        mapDependentItems.add(applyBef);
        mapDependentItems.add(savePlan);

        JMenuItem reset = new JMenuItem("Reset View");
        reset.addActionListener(e -> { mapPanel.resetView(); mapPanel.repaint(); });
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());

        fileMenu.add(importRdh);
        fileMenu.add(presetsMenu);
        fileMenu.add(importDra);
        fileMenu.add(applyBef);
        fileMenu.addSeparator();
        fileMenu.add(savePlan);
        fileMenu.addSeparator();
        fileMenu.add(reset);
        fileMenu.addSeparator();
        fileMenu.add(exit);
        return fileMenu;
    }

    private JMenu buildViewMenu() {
        JMenu viewMenu = new JMenu("View");

        ButtonGroup colorGroup = new ButtonGroup();
        JRadioButtonMenuItem byDistrict =
                new JRadioButtonMenuItem("Color by district", true);
        JRadioButtonMenuItem byLean =
                new JRadioButtonMenuItem("Color by partisan lean (red ⇄ blue)");
        byDistrict.addActionListener(e -> mapPanel.setViewMode(MapPanel.ViewMode.DISTRICT));
        byLean.addActionListener(e -> mapPanel.setViewMode(MapPanel.ViewMode.PARTISAN_LEAN));
        colorGroup.add(byDistrict);
        colorGroup.add(byLean);

        JCheckBoxMenuItem showPrecinctLines =
                new JCheckBoxMenuItem("Show precinct lines", true);
        showPrecinctLines.addActionListener(e ->
                mapPanel.setShowPrecinctLines(showPrecinctLines.isSelected()));

        JCheckBoxMenuItem showDistrictLines =
                new JCheckBoxMenuItem("Show district lines", true);
        showDistrictLines.addActionListener(e ->
                mapPanel.setShowDistrictLines(showDistrictLines.isSelected()));

        JCheckBoxMenuItem showDistrictNumbers =
                new JCheckBoxMenuItem("Show district numbers", true);
        showDistrictNumbers.addActionListener(e ->
                mapPanel.setShowDistrictNumbers(showDistrictNumbers.isSelected()));

        viewMenu.add(byDistrict);
        viewMenu.add(byLean);
        viewMenu.addSeparator();
        viewMenu.add(showPrecinctLines);
        viewMenu.add(showDistrictLines);
        viewMenu.add(showDistrictNumbers);
        return viewMenu;
    }

    private JMenu buildAiMenu() {
        JMenu aiMenu = new JMenu("AI");
        JMenuItem analyze = new JMenuItem("Analyze Fairness");
        analyze.addActionListener(e -> doAnalyze());
        JMenuItem optimize = new JMenuItem("Optimize Fairness");
        optimize.addActionListener(e -> doOptimize());
        JMenuItem generate = new JMenuItem("Generate Plan…");
        generate.addActionListener(e -> doGenerate());
        mapDependentItems.add(analyze);
        mapDependentItems.add(optimize);
        mapDependentItems.add(generate);

        aiMenu.add(generate);
        aiMenu.addSeparator();
        aiMenu.add(analyze);
        aiMenu.add(optimize);
        return aiMenu;
    }

    private JMenu buildHelpMenu() {
        JMenu helpMenu = new JMenu("Help");
        JMenuItem tutorial = new JMenuItem("Tutorial");
        tutorial.addActionListener(e -> TutorialDialog.show(this));
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Redistricting Fairness Analyzer\n\n"
                + "Imports precinct data from the Redistricting Data Hub,\n"
                + "generates plans with five different algorithms (Simple,\n"
                + "Advanced multi-objective, Compactness, Competitive,\n"
                + "Partisan target), and analyses fairness with population\n"
                + "deviation, Polsby–Popper compactness, and the efficiency gap.",
                "About", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(tutorial);
        helpMenu.addSeparator();
        helpMenu.add(about);
        return helpMenu;
    }

    private void refreshMapDependentItems() {
        boolean ok = mapPanel.getMap() != null;
        for (JMenuItem item : mapDependentItems) item.setEnabled(ok);
    }

    // ---------- file actions ---------------------------------------------

    private void doImportRdh() {
        Path path = NativeFileChooser.showOpen(this,
                "Import RDH precinct file", "geojson", "json");
        if (path == null) return;
        try {
            RedistrictingMap map = RdhPrecinctLoader.loadPrecinctsGeoJson(path);
            mapPanel.setMap(map);
            statusBar.setText(" " + describe(map));
            refreshMapDependentItems();
        } catch (IOException | RuntimeException ex) {
            showError("Import failed", ex);
        }
    }

    private void doLoadPreset(Presets.Preset preset) {
        try {
            RedistrictingMap map = preset.load();
            mapPanel.setMap(map);
            statusBar.setText(" " + describe(map));
            refreshMapDependentItems();
        } catch (IOException | RuntimeException ex) {
            showError("Preset load failed", ex);
        }
    }

    private void doImportDra() {
        Path path = NativeFileChooser.showOpen(this,
                "Import file exported from Dave's Redistricting",
                "geojson", "json", "csv");
        if (path == null) return;
        try {
            DraImporter.Result result = DraImporter.importFile(path, mapPanel.getMap());
            mapPanel.setMap(result.map());
            statusBar.setText(" " + result.description());
            refreshMapDependentItems();
        } catch (IOException | RuntimeException ex) {
            showError("Import failed", ex);
        }
    }

    private void doApplyBef() {
        if (mapPanel.getMap() == null) return;
        Path path = NativeFileChooser.showOpen(this,
                "Apply block-equivalency CSV", "csv", "txt", "bef");
        if (path == null) return;
        try {
            RedistrictingMap map = RdhPrecinctLoader.applyBef(mapPanel.getMap(), path);
            mapPanel.setMap(map);
            statusBar.setText(" " + describe(map));
        } catch (IOException | RuntimeException ex) {
            showError("BEF failed", ex);
        }
    }

    private void doSavePlan() {
        RedistrictingMap map = mapPanel.getMap();
        if (map == null) return;
        Path path = NativeFileChooser.showSave(this,
                "Save plan as GeoJSON", "plan.geojson", "geojson", "json");
        if (path == null) return;
        if (!path.toString().toLowerCase().endsWith(".geojson")
                && !path.toString().toLowerCase().endsWith(".json")) {
            path = path.resolveSibling(path.getFileName() + ".geojson");
        }
        try {
            PlanGeoJsonWriter.write(map, path);
            statusBar.setText(" Saved plan to " + path);
        } catch (IOException | RuntimeException ex) {
            showError("Save failed", ex);
        }
    }

    // ---------- AI actions -----------------------------------------------

    private void doAnalyze() {
        RedistrictingMap map = mapPanel.getMap();
        if (map == null) return;
        FairnessReport report = analyzer.analyze(map);
        showReport("Fairness Report", report.prettyPrint(map.name()));
    }

    private void doOptimize() {
        RedistrictingMap map = mapPanel.getMap();
        if (map == null) return;
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

    private void doGenerate() {
        GenerationParams params = GenerateDialog.showDialog(this);
        if (params == null) return;
        RedistrictingMap baseMap = mapPanel.getMap();
        if (baseMap == null) return;
        statusBar.setText(" Generating plan… (" + params.attempts() + " attempts)");
        new Thread(() -> {
            try {
                PrecinctBase base = PrecinctBase.fromMap(baseMap);
                RedistrictingMap plan = new MapGenerator().generate(base, params);
                SwingUtilities.invokeLater(() -> {
                    mapPanel.setMap(plan);
                    statusBar.setText(" " + describe(plan));
                });
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() ->
                        showError("Generate failed", ex));
            }
        }, "plan-generator").start();
    }

    // ---------- utility --------------------------------------------------

    private void showReport(String title, String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(620, 460));
        JOptionPane.showMessageDialog(this, scroll, title,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String title, Throwable ex) {
        JOptionPane.showMessageDialog(this,
                ex.getMessage() == null ? ex.toString() : ex.getMessage(),
                title, JOptionPane.ERROR_MESSAGE);
    }

    private static String describe(RedistrictingMap map) {
        return map.name() + " — " + map.precincts().size() + " precincts, "
                + map.districtCount() + " districts";
    }

    /**
     * Application entry. Installs the dark theme, prompts the user to load
     * RDH precinct data (or use the bundled sample), and only then opens
     * the main window.
     */
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            DarkTheme.install();
            RedistrictingMap base = RdhStartupDialog.show(null);
            if (base == null) {
                // User cancelled the startup dialog — exit cleanly.
                return;
            }
            MainFrame f = new MainFrame(base);
            f.setVisible(true);
            TutorialDialog.showOnFirstLaunch(f);
        });
    }
}
