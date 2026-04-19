package com.redistricting.gui;

import com.redistricting.ai.FairnessAnalyzer;
import com.redistricting.ai.FairnessReport;
import com.redistricting.ai.GenerationParams;
import com.redistricting.ai.MapGenerator;
import com.redistricting.ai.algorithms.PrecinctBase;
import com.redistricting.io.DistrictShapesGeoJsonWriter;
import com.redistricting.io.DraImporter;
import com.redistricting.io.PlanGeoJsonWriter;
import com.redistricting.io.Presets;
import com.redistricting.io.RdhPrecinctLoader;
import com.redistricting.model.Precinct;
import com.redistricting.model.RedistrictingMap;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application window.
 *
 * <p>Layout: a streamlined top toolbar with the controls users actually
 * reach for during a session (district selector, brush size, edit toggle,
 * view mode, view-layer toggles, fairness, generate, export); a
 * {@link MapPanel} canvas in the centre; and a status bar at the bottom.
 * The legacy menu bar is retained but trimmed — every menu item now also
 * has a toolbar shortcut.
 *
 * <p>On launch the app boots straight into the bundled North Carolina
 * preset so a real, navigable map is on screen immediately. Users can pick
 * a different base from <em>File &rarr; Choose base data&hellip;</em>.
 */
public final class MainFrame extends JFrame {

    private final MapPanel mapPanel = new MapPanel();
    private final JLabel statusBar = new JLabel(" Ready");
    private final FairnessAnalyzer analyzer = new FairnessAnalyzer();
    private final List<JComponent> mapDependentItems = new ArrayList<>();

    // Toolbar widgets (held for refresh after the map changes).
    private final JComboBox<String> districtPicker = new JComboBox<>();
    private final JSpinner brushSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 200, 2));
    private final JToggleButton editToggle = new JToggleButton("✏  Edit");
    private final JComboBox<ViewModeItem> viewModePicker = new JComboBox<>();
    private final JButton undoBtn = new JButton("↶ Undo");
    private final JButton redoBtn = new JButton("↷ Redo");

    public MainFrame(RedistrictingMap initialMap) {
        super("Redistricting Studio");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(buildMenuBar());

        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        statusBar.setFont(statusBar.getFont().deriveFont(12f));

        add(buildToolbar(), BorderLayout.NORTH);
        add(mapPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        setSize(new Dimension(1200, 820));
        setLocationRelativeTo(null);

        mapPanel.setDarkMode(true);
        mapPanel.setChangeListener(m -> {
            statusBar.setText(" " + describe(m));
            refreshUndoRedo();
            refreshDistrictPicker(); // active district may now have changed pop
        });

        installShortcuts();

        if (initialMap != null) {
            adoptMap(initialMap);
        }
        refreshMapDependentItems();
    }

    // ---------- toolbar --------------------------------------------------

    private JComponent buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setRollover(true);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        bar.setBackground(DarkTheme.BACKGROUND_ALT);

        // ----- Edit cluster -----
        editToggle.setToolTipText("Paint precincts into the active district (E)");
        editToggle.addActionListener(e -> {
            mapPanel.setEditMode(editToggle.isSelected());
            updateStatus();
        });
        bar.add(editToggle);
        bar.add(Box.createHorizontalStrut(8));

        bar.add(label("District:"));
        districtPicker.setMaximumSize(new Dimension(220, 28));
        districtPicker.addActionListener(e -> {
            int idx = districtPicker.getSelectedIndex();
            if (idx >= 0) mapPanel.setActiveDistrict(idx);
        });
        bar.add(districtPicker);
        bar.add(Box.createHorizontalStrut(8));

        bar.add(label("Brush:"));
        brushSpinner.setMaximumSize(new Dimension(70, 28));
        brushSpinner.setToolTipText("Brush radius in pixels (0 = single precinct, [ / ] to adjust)");
        brushSpinner.addChangeListener(e ->
                mapPanel.setBrushSize((Integer) brushSpinner.getValue()));
        bar.add(brushSpinner);
        bar.add(Box.createHorizontalStrut(8));

        undoBtn.addActionListener(e -> { mapPanel.undo(); refreshUndoRedo(); });
        redoBtn.addActionListener(e -> { mapPanel.redo(); refreshUndoRedo(); });
        bar.add(undoBtn);
        bar.add(redoBtn);
        mapDependentItems.add(undoBtn);
        mapDependentItems.add(redoBtn);

        bar.add(separator());

        // ----- View cluster -----
        bar.add(label("View:"));
        viewModePicker.setModel(new DefaultComboBoxModel<>(new ViewModeItem[] {
                new ViewModeItem("By district", MapPanel.ViewMode.DISTRICT),
                new ViewModeItem("Whole districts", MapPanel.ViewMode.DISTRICT_WHOLE),
                new ViewModeItem("Partisan lean", MapPanel.ViewMode.PARTISAN_LEAN),
                new ViewModeItem("Partisan lean (by district)", MapPanel.ViewMode.PARTISAN_LEAN_DISTRICT),
        }));
        viewModePicker.setMaximumSize(new Dimension(220, 28));
        viewModePicker.addActionListener(e -> {
            ViewModeItem v = (ViewModeItem) viewModePicker.getSelectedItem();
            if (v != null) mapPanel.setViewMode(v.mode);
        });
        bar.add(viewModePicker);

        bar.add(separator());

        // ----- Action cluster -----
        JButton generate = new JButton("⚙  Generate…");
        generate.setToolTipText("Generate a new plan with the AI (G)");
        generate.addActionListener(e -> doGenerate());
        bar.add(generate);
        mapDependentItems.add(generate);

        JButton analyze = new JButton("📊  Analyze");
        analyze.setToolTipText("Run fairness analysis on the current plan (A)");
        analyze.addActionListener(e -> doAnalyze());
        bar.add(analyze);
        mapDependentItems.add(analyze);

        JButton optimize = new JButton("✦  Optimize");
        optimize.setToolTipText("Let the AI swap boundary precincts to improve fairness");
        optimize.addActionListener(e -> doOptimize());
        bar.add(optimize);
        mapDependentItems.add(optimize);

        bar.add(Box.createHorizontalGlue());

        JButton newPlan = new JButton("📄  New plan…");
        newPlan.setToolTipText("Start a fresh plan from this base (choose # of districts)");
        newPlan.addActionListener(e -> doNewBlankPlan());
        bar.add(newPlan);
        mapDependentItems.add(newPlan);

        JButton export = new JButton("💾  Export…");
        export.setToolTipText("Export the current plan as GeoJSON");
        export.addActionListener(e -> doExportPrecinctGeoJson());
        bar.add(export);
        mapDependentItems.add(export);

        return bar;
    }

    private static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(DarkTheme.FOREGROUND_DIM);
        l.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));
        return l;
    }

    private static JComponent separator() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setMaximumSize(new Dimension(1, 24));
        s.setForeground(DarkTheme.BORDER);
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        wrap.setOpaque(false);
        wrap.add(s);
        wrap.setMaximumSize(new Dimension(20, 28));
        return wrap;
    }

    private void installShortcuts() {
        JComponent root = (JComponent) getContentPane();
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_E, 0), "edit", e -> {
            editToggle.setSelected(!editToggle.isSelected());
            mapPanel.setEditMode(editToggle.isSelected());
            updateStatus();
        });
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_G, 0), "gen", e -> doGenerate());
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "ana", e -> doAnalyze());
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0), "brushDown",
                e -> brushSpinner.setValue(Math.max(0, (Integer) brushSpinner.getValue() - 4)));
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, 0), "brushUp",
                e -> brushSpinner.setValue(Math.min(200, (Integer) brushSpinner.getValue() + 4)));
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                InputEvent.CTRL_DOWN_MASK), "undo", e -> { mapPanel.undo(); refreshUndoRedo(); });
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Y,
                InputEvent.CTRL_DOWN_MASK), "redo", e -> { mapPanel.redo(); refreshUndoRedo(); });
    }

    private void bind(JComponent c, KeyStroke ks, String name,
                      java.util.function.Consumer<java.awt.event.ActionEvent> action) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                action.accept(e);
            }
        });
    }

    // ---------- menu wiring ----------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(buildFileMenu());
        bar.add(buildEditMenu());
        bar.add(buildViewMenu());
        bar.add(buildAiMenu());
        bar.add(buildHelpMenu());
        return bar;
    }

    private JMenu buildFileMenu() {
        JMenu fileMenu = new JMenu("File");

        JMenu chooseBase = new JMenu("Choose base data");
        for (Presets.Preset preset : Presets.all()) {
            JMenuItem mi = new JMenuItem(preset.displayName());
            mi.addActionListener(e -> doLoadPreset(preset));
            chooseBase.add(mi);
        }
        chooseBase.addSeparator();
        JMenuItem importRdh = new JMenuItem("Import precinct data from RDH…");
        importRdh.addActionListener(e -> doImportRdh());
        chooseBase.add(importRdh);
        JMenuItem importDra = new JMenuItem("Import plan from Dave's Redistricting…");
        importDra.addActionListener(e -> doImportDra());
        chooseBase.add(importDra);
        JMenuItem applyBef = new JMenuItem("Apply block-equivalency file (CSV BEF)…");
        applyBef.addActionListener(e -> doApplyBef());
        chooseBase.add(applyBef);
        mapDependentItems.add(applyBef);

        JMenuItem newPlan = new JMenuItem("New blank plan…");
        newPlan.addActionListener(e -> doNewBlankPlan());
        mapDependentItems.add(newPlan);

        JMenuItem exportPrecinct = new JMenuItem("Export precinct GeoJSON (round-trip)…");
        exportPrecinct.addActionListener(e -> doExportPrecinctGeoJson());
        mapDependentItems.add(exportPrecinct);
        JMenuItem exportDistrict = new JMenuItem("Export district shapes GeoJSON…");
        exportDistrict.addActionListener(e -> doExportDistrictGeoJson());
        mapDependentItems.add(exportDistrict);

        JMenuItem reset = new JMenuItem("Reset View");
        reset.addActionListener(e -> { mapPanel.resetView(); mapPanel.repaint(); });
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());

        fileMenu.add(chooseBase);
        fileMenu.add(newPlan);
        fileMenu.addSeparator();
        fileMenu.add(exportPrecinct);
        fileMenu.add(exportDistrict);
        fileMenu.addSeparator();
        fileMenu.add(reset);
        fileMenu.addSeparator();
        fileMenu.add(exit);
        return fileMenu;
    }

    private JMenu buildEditMenu() {
        JMenu editMenu = new JMenu("Edit");
        JMenuItem undo = new JMenuItem("Undo");
        undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undo.addActionListener(e -> { mapPanel.undo(); refreshUndoRedo(); });
        JMenuItem redo = new JMenuItem("Redo");
        redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        redo.addActionListener(e -> { mapPanel.redo(); refreshUndoRedo(); });
        editMenu.add(undo);
        editMenu.add(redo);
        return editMenu;
    }

    private JMenu buildViewMenu() {
        JMenu viewMenu = new JMenu("View");

        ButtonGroup colorGroup = new ButtonGroup();
        for (int i = 0; i < viewModePicker.getModel().getSize(); i++) {
            ViewModeItem item = viewModePicker.getItemAt(i);
            JRadioButtonMenuItem mi = new JRadioButtonMenuItem(item.label, i == 0);
            colorGroup.add(mi);
            final int idx = i;
            mi.addActionListener(e -> viewModePicker.setSelectedIndex(idx));
            viewMenu.add(mi);
        }
        viewMenu.addSeparator();

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
                "Redistricting Studio\n\n"
                + "Loads precinct data, lets you draw / edit / generate plans,\n"
                + "analyses fairness with population deviation, Polsby–Popper\n"
                + "compactness, and the efficiency gap, and exports the result\n"
                + "as precinct- or district-level GeoJSON.",
                "About", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(tutorial);
        helpMenu.addSeparator();
        helpMenu.add(about);
        return helpMenu;
    }

    private void refreshMapDependentItems() {
        boolean ok = mapPanel.getMap() != null;
        for (JComponent item : mapDependentItems) item.setEnabled(ok);
        refreshUndoRedo();
    }

    private void refreshUndoRedo() {
        undoBtn.setEnabled(mapPanel.getMap() != null && mapPanel.canUndo());
        redoBtn.setEnabled(mapPanel.getMap() != null && mapPanel.canRedo());
    }

    private void refreshDistrictPicker() {
        RedistrictingMap m = mapPanel.getMap();
        if (m == null) {
            districtPicker.setModel(new DefaultComboBoxModel<>(new String[0]));
            return;
        }
        String[] entries = new String[m.districtCount()];
        for (int i = 0; i < entries.length; i++) {
            int pop = m.districts().get(i).totalPopulation();
            entries[i] = String.format("D%d  (pop %,d)", i + 1, pop);
        }
        int prevSel = mapPanel.getActiveDistrict();
        districtPicker.setModel(new DefaultComboBoxModel<>(entries));
        if (prevSel >= 0 && prevSel < entries.length) {
            districtPicker.setSelectedIndex(prevSel);
        }
    }

    private void adoptMap(RedistrictingMap m) {
        mapPanel.setMap(m);
        refreshDistrictPicker();
        refreshMapDependentItems();
        updateStatus();
    }

    private void updateStatus() {
        RedistrictingMap m = mapPanel.getMap();
        if (m == null) { statusBar.setText(" Ready"); return; }
        StringBuilder sb = new StringBuilder(" ").append(describe(m));
        if (mapPanel.isEditMode()) {
            sb.append("   •   Editing D").append(mapPanel.getActiveDistrict() + 1)
              .append("   •   Brush ").append(mapPanel.getBrushSize()).append("px");
        }
        statusBar.setText(sb.toString());
    }

    // ---------- file actions ---------------------------------------------

    private void doImportRdh() {
        Path path = NativeFileChooser.showOpen(this,
                "Import RDH precinct file", "geojson", "json");
        if (path == null) return;
        try {
            adoptMap(RdhPrecinctLoader.loadPrecinctsGeoJson(path));
        } catch (IOException | RuntimeException ex) {
            showError("Import failed", ex);
        }
    }

    private void doLoadPreset(Presets.Preset preset) {
        try {
            adoptMap(preset.load());
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
            adoptMap(result.map());
            statusBar.setText(" " + result.description());
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
            adoptMap(RdhPrecinctLoader.applyBef(mapPanel.getMap(), path));
        } catch (IOException | RuntimeException ex) {
            showError("BEF failed", ex);
        }
    }

    private void doExportPrecinctGeoJson() {
        RedistrictingMap map = mapPanel.getMap();
        if (map == null) return;
        Path path = NativeFileChooser.showSave(this,
                "Export plan as precinct GeoJSON", "plan.geojson", "geojson", "json");
        if (path == null) return;
        path = ensureExt(path, ".geojson");
        try {
            PlanGeoJsonWriter.write(map, path);
            statusBar.setText(" Exported plan to " + path);
        } catch (IOException | RuntimeException ex) {
            showError("Export failed", ex);
        }
    }

    private void doExportDistrictGeoJson() {
        RedistrictingMap map = mapPanel.getMap();
        if (map == null) return;
        Path path = NativeFileChooser.showSave(this,
                "Export district shapes as GeoJSON", "districts.geojson", "geojson", "json");
        if (path == null) return;
        path = ensureExt(path, ".geojson");
        try {
            DistrictShapesGeoJsonWriter.write(map, path);
            statusBar.setText(" Exported district shapes to " + path);
        } catch (IOException | RuntimeException ex) {
            showError("Export failed", ex);
        }
    }

    private static Path ensureExt(Path path, String ext) {
        String s = path.toString().toLowerCase();
        if (s.endsWith(".geojson") || s.endsWith(".json")) return path;
        return path.resolveSibling(path.getFileName() + ext);
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
                SwingUtilities.invokeLater(() -> adoptMap(plan));
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() ->
                        showError("Generate failed", ex));
            }
        }, "plan-generator").start();
    }

    private void doNewBlankPlan() {
        RedistrictingMap base = mapPanel.getMap();
        if (base == null) return;
        Object choice = JOptionPane.showInputDialog(this,
                "How many districts?\nAll precincts will start in district 1\n"
                        + "so you can paint your own plan from scratch.",
                "New blank plan",
                JOptionPane.QUESTION_MESSAGE, null, null,
                String.valueOf(Math.max(2, base.districtCount())));
        if (choice == null) return;
        int n;
        try { n = Integer.parseInt(choice.toString().trim()); }
        catch (NumberFormatException ex) {
            showError("Invalid number", ex); return;
        }
        if (n < 1 || n > 200) {
            JOptionPane.showMessageDialog(this,
                    "Pick a district count between 1 and 200.",
                    "Out of range", JOptionPane.WARNING_MESSAGE);
            return;
        }
        adoptMap(blankPlanFrom(base, n));
        editToggle.setSelected(true);
        mapPanel.setEditMode(true);
        updateStatus();
    }

    /**
     * Build a fresh {@link RedistrictingMap} that reuses {@code base}'s
     * precinct geometry but starts every precinct in district 0 and exposes
     * the requested number of seats. Population &amp; vote totals are
     * preserved exactly; nothing is dropped.
     */
    private static RedistrictingMap blankPlanFrom(RedistrictingMap base, int districtCount) {
        List<Precinct> copies = new ArrayList<>(base.precincts().size());
        for (Precinct p : base.precincts()) {
            copies.add(new Precinct(p.id(), 0, p.population(),
                    p.demVotes(), p.repVotes(), p.rings()));
        }
        return new RedistrictingMap(base.name() + " — blank plan",
                districtCount, copies);
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
        return String.format("%s — %,d precincts, %d districts, total pop %,d",
                map.name(), map.precincts().size(), map.districtCount(),
                map.totalPopulation());
    }

    /** Combo box entry: human label + view mode it represents. */
    private static final class ViewModeItem {
        final String label;
        final MapPanel.ViewMode mode;
        ViewModeItem(String l, MapPanel.ViewMode m) { this.label = l; this.mode = m; }
        @Override public String toString() { return label; }
    }

    /**
     * Application entry. Installs the dark theme, loads the bundled North
     * Carolina preset, and opens the main window.
     *
     * <p>Falls back to {@link RdhStartupDialog} only when the preset fails
     * to load (e.g. the JAR was assembled without resources). The user can
     * still pick another base from <em>File &rarr; Choose base data&hellip;</em>
     * at any point.
     */
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            DarkTheme.install();
            RedistrictingMap base = null;
            try {
                base = Presets.NC_2024_PRESIDENTIAL.load();
            } catch (IOException | RuntimeException ex) {
                base = RdhStartupDialog.show(null);
                if (base == null) return;
            }
            MainFrame f = new MainFrame(base);
            f.setVisible(true);
            TutorialDialog.showOnFirstLaunch(f);
        });
    }
}
