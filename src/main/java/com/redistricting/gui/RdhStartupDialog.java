package com.redistricting.gui;

import com.redistricting.io.DraGeoJsonLoader;
import com.redistricting.io.RdhPrecinctLoader;
import com.redistricting.model.RedistrictingMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Modal startup dialog shown before the main window becomes interactive.
 *
 * <p>Redistricting is a precinct-level operation, so the user must choose a
 * precinct dataset before any menu is enabled. They have three options:
 * <ol>
 *   <li><strong>Import RDH precinct file</strong> — pick a state-level
 *       precinct GeoJSON downloaded from the
 *       <a href="https://redistrictingdatahub.org/">Redistricting Data Hub</a>.</li>
 *   <li><strong>Open an existing plan</strong> — any DRA-compatible export
 *       (geojson / json / csv).</li>
 *   <li><strong>Use bundled demo data</strong> — the small bundled sample,
 *       enough to explore the UI without downloading anything.</li>
 * </ol>
 *
 * <p>Cancelling closes the application; the API requires a valid base map
 * before {@code MainFrame} can run.
 */
public final class RdhStartupDialog extends JDialog {

    private RedistrictingMap result;
    private boolean cancelled = false;

    private RdhStartupDialog(Frame owner) {
        super(owner, "Load precinct data — Redistricting Data Hub", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (result == null) cancelled = true;
            }
        });

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        root.add(buildIntro(), BorderLayout.NORTH);
        root.add(buildButtons(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
        setMinimumSize(new Dimension(560, 0));
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Show the dialog and block until the user chooses a base or cancels.
     *
     * @return the selected base map, or {@code null} if the user cancelled.
     */
    public static RedistrictingMap show(Frame owner) {
        RdhStartupDialog d = new RdhStartupDialog(owner);
        d.setVisible(true);
        return d.result;
    }

    private JComponent buildIntro() {
        JLabel title = new JLabel("Select state precinct data to begin");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        JLabel body = new JLabel("<html><body style='width:480px'>"
                + "Redistricting works at the precinct level. Choose a statewide "
                + "precinct file from the <b>Redistricting Data Hub</b> "
                + "(<i>redistrictingdatahub.org</i> — pick any state's "
                + "<i>precinct shapefile/GeoJSON</i> with election results), "
                + "open an existing plan you have saved, or use the small "
                + "bundled sample to explore the app."
                + "</body></html>");
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(title);
        p.add(Box.createVerticalStrut(8));
        p.add(body);
        return p;
    }

    private JComponent buildButtons() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        p.add(makeButton("Import precinct GeoJSON from Redistricting Data Hub…",
                "Load a statewide precinct file (.geojson) downloaded from RDH",
                this::onLoadRdh));
        p.add(Box.createVerticalStrut(8));
        p.add(makeButton("Open existing plan (DRA / GeoJSON / Map Archive / CSV)…",
                "Open any plan you have saved or exported from Dave's Redistricting",
                this::onOpenExisting));
        p.add(Box.createVerticalStrut(8));
        p.add(makeButton("Use bundled demo data (small sample)",
                "Load the bundled sample so you can explore the UI without downloading anything",
                this::onUseSample));
        return p;
    }

    private JButton makeButton(String text, String tip, Runnable action) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setHorizontalAlignment(JButton.LEFT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height + 6));
        b.addActionListener(e -> action.run());
        return b;
    }

    private JComponent buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        JButton cancel = new JButton("Cancel — exit application");
        cancel.addActionListener(e -> { cancelled = true; result = null; dispose(); });
        p.add(cancel, BorderLayout.EAST);
        return p;
    }

    private void onLoadRdh() {
        Path path = NativeFileChooser.showOpen((Frame) getOwner(),
                "Select state precinct data from RDH", "geojson", "json");
        if (path == null) return;
        runLoad(() -> RdhPrecinctLoader.loadPrecinctsGeoJson(path),
                "RDH precinct file");
    }

    private void onOpenExisting() {
        Path path = NativeFileChooser.showOpen((Frame) getOwner(),
                "Open existing plan", "geojson", "json", "csv");
        if (path == null) return;
        runLoad(() -> com.redistricting.io.DraImporter.importFile(path, null).map(),
                "existing plan");
    }

    private void onUseSample() {
        runLoad(() -> DraGeoJsonLoader.loadFromResource("sample-dra.geojson"),
                "bundled sample");
    }

    private void runLoad(LoaderTask task, String what) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            RedistrictingMap map = task.run();
            if (map == null) {
                throw new IOException("loader returned no map");
            }
            result = map;
            dispose();
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not load " + what + ":\n" + ex.getMessage(),
                    "Load failed", JOptionPane.ERROR_MESSAGE);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    public boolean wasCancelled() { return cancelled; }

    @FunctionalInterface
    private interface LoaderTask {
        RedistrictingMap run() throws IOException;
    }
}
