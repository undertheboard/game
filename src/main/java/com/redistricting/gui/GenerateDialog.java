package com.redistricting.gui;

import com.redistricting.ai.GenerationParams;
import com.redistricting.ai.algorithms.Algorithms;
import com.redistricting.ai.algorithms.RedistrictingAlgorithm;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.ButtonGroup;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Modal dialog that gathers {@link GenerationParams} from the user.
 *
 * <p>Two modes are exposed:
 * <ul>
 *   <li><strong>Simple</strong> — pick the number of districts (and a seed)
 *       and let the {@link com.redistricting.ai.algorithms.SimpleAlgorithm}
 *       produce a balanced contiguous plan with sensible defaults.</li>
 *   <li><strong>Advanced</strong> — pick any of the registered algorithms
 *       (Advanced multi-objective / Compactness / Competitive /
 *       Partisan target) and tune every knob: partisan bias, county
 *       adherence, compactness weight, population tolerance, reliability,
 *       and the random seed.</li>
 * </ul>
 *
 * <p>The synthetic-grid sizing fields (precincts X/Y, counties X/Y) are
 * still here for the legacy demo path but are not surfaced when the dialog
 * is constructed in "real precinct base" mode (the default).
 */
public final class GenerateDialog extends JDialog {

    // --- shared core fields --------------------------------------------------
    private final JSpinner districtsSpinner =
            new JSpinner(new SpinnerNumberModel(8, 2, 50, 1));
    private final JSpinner seedSpinner =
            new JSpinner(new SpinnerNumberModel(System.currentTimeMillis() & 0xFFFFFFFL,
                    0L, Long.MAX_VALUE, 1L));

    // --- advanced-only fields ------------------------------------------------
    private final JComboBox<RedistrictingAlgorithm> algorithmCombo =
            new JComboBox<>(new DefaultComboBoxModel<>(
                    Algorithms.ALL.toArray(new RedistrictingAlgorithm[0])));

    private final JSlider biasSlider        = labelledSlider(-100, 100, 0, 25);
    private final JSlider countySlider      = labelledSlider(0, 100, 50, 25);
    private final JSlider compactSlider     = labelledSlider(0, 100, 50, 25);
    private final JSlider popTolSlider      = labelledSlider(0, 100, 20, 25); // ‰
    private final JSlider reliabilitySlider = labelledSlider(0, 100, 50, 25);

    // --- synthetic-grid (legacy) fields -------------------------------------
    private final JSpinner precinctsXSpinner =
            new JSpinner(new SpinnerNumberModel(20, 4, 200, 1));
    private final JSpinner precinctsYSpinner =
            new JSpinner(new SpinnerNumberModel(20, 4, 200, 1));
    private final JSpinner countiesXSpinner =
            new JSpinner(new SpinnerNumberModel(5, 1, 40, 1));
    private final JSpinner countiesYSpinner =
            new JSpinner(new SpinnerNumberModel(5, 1, 40, 1));

    private final JLabel algoDescription = new JLabel();

    private final JRadioButton simpleMode   = new JRadioButton("Simple", true);
    private final JRadioButton advancedMode = new JRadioButton("Advanced");

    private GenerationParams result;
    private final boolean showSyntheticControls;

    private GenerateDialog(Frame owner, boolean showSyntheticControls) {
        super(owner, "Generate Plan", true);
        this.showSyntheticControls = showSyntheticControls;

        ButtonGroup group = new ButtonGroup();
        group.add(simpleMode);
        group.add(advancedMode);
        simpleMode.addActionListener(e -> updateMode());
        advancedMode.addActionListener(e -> updateMode());

        algorithmCombo.addActionListener(e -> updateDescription());
        algorithmCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    javax.swing.JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                String label = (value instanceof RedistrictingAlgorithm a)
                        ? a.displayName() : String.valueOf(value);
                return super.getListCellRendererComponent(list, label, index,
                        isSelected, cellHasFocus);
            }
        });
        algorithmCombo.setSelectedItem(Algorithms.ADVANCED);
        updateDescription();

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        updateMode();
        pack();
        setMinimumSize(new Dimension(620, getHeight()));
        setLocationRelativeTo(owner);
    }

    /** Show the dialog in its standard mode (no synthetic-grid controls). */
    public static GenerationParams showDialog(Frame owner) {
        return showDialog(owner, false);
    }

    /**
     * Show the dialog. When {@code showSyntheticControls} is true, the
     * legacy precinct-grid / county-grid spinners are exposed (used by the
     * demo path that runs against a synthetic base map).
     */
    public static GenerationParams showDialog(Frame owner, boolean showSyntheticControls) {
        GenerateDialog d = new GenerateDialog(owner, showSyntheticControls);
        d.setVisible(true);
        return d.result;
    }

    // ---------- layout ---------------------------------------------------

    private JComponent buildHeader() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));
        p.add(new JLabel("Mode:"));
        p.add(simpleMode);
        p.add(advancedMode);
        return p;
    }

    private JComponent buildBody() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Plan", buildSimpleTab());
        tabs.addTab("Algorithm & objectives", buildAdvancedTab());
        if (showSyntheticControls) tabs.addTab("Demo grid", buildGridTab());
        return tabs;
    }

    private JPanel buildSimpleTab() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = baseConstraints();
        int row = 0;
        addRow(form, c, row++, "Districts:", districtsSpinner,
                "Number of seats to draw");
        addRow(form, c, row++, "Random seed:", seedSpinner,
                "Same seed + same params reproduces the same map");
        return form;
    }

    private JPanel buildAdvancedTab() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = baseConstraints();
        int row = 0;
        addRow(form, c, row++, "Algorithm:", algorithmCombo,
                "Which redistricting strategy to run");
        c.gridx = 1; c.gridy = row++; c.gridwidth = 1;
        algoDescription.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        form.add(algoDescription, c);
        c.gridwidth = 1;
        addRow(form, c, row++, "Partisan bias  (R+100 ⇄ D+100):", biasSlider,
                "Negative = Republican-favoring, positive = Democratic-favoring");
        addRow(form, c, row++, "County-line adherence (%):", countySlider,
                "Penalty for splitting counties between districts");
        addRow(form, c, row++, "Compactness (%):", compactSlider,
                "Weight on geometric compactness in the growth heuristic");
        addRow(form, c, row++, "Population tolerance (‰ of ideal):", popTolSlider,
                "Allowed |population deviation| from the ideal district size");
        addRow(form, c, row++, "Reliability (% — # restarts):", reliabilitySlider,
                "Higher = more independent attempts; the best is kept");
        return form;
    }

    private JPanel buildGridTab() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = baseConstraints();
        int row = 0;
        addRow(form, c, row++, "Precincts (X × Y):",
                twin(precinctsXSpinner, precinctsYSpinner),
                "Resolution of the synthetic precinct grid (demo only)");
        addRow(form, c, row++, "Counties (X × Y):",
                twin(countiesXSpinner, countiesYSpinner),
                "How many counties the precincts are grouped into (demo only)");
        return form;
    }

    private void updateMode() {
        boolean adv = advancedMode.isSelected();
        for (JComponent c : new JComponent[] {
                algorithmCombo, biasSlider, countySlider, compactSlider,
                popTolSlider, reliabilitySlider, algoDescription }) {
            c.setEnabled(adv);
        }
        if (!adv) algorithmCombo.setSelectedItem(Algorithms.SIMPLE);
        else if (algorithmCombo.getSelectedItem() == Algorithms.SIMPLE) {
            algorithmCombo.setSelectedItem(Algorithms.ADVANCED);
        }
        updateDescription();
    }

    private void updateDescription() {
        Object item = algorithmCombo.getSelectedItem();
        if (item instanceof RedistrictingAlgorithm a) {
            algoDescription.setText("<html><body style='width:420px'><i>"
                    + a.description() + "</i></body></html>");
        }
    }

    // ---------- helpers ---------------------------------------------------

    private static GridBagConstraints baseConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }

    private static JComponent twin(JComponent a, JComponent b) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.add(a); p.add(new JLabel(" × ")); p.add(b);
        return p;
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row,
                               String label, JComponent field, String tip) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        JLabel l = new JLabel(label);
        l.setToolTipText(tip);
        form.add(l, c);
        c.gridx = 1; c.weightx = 1;
        field.setToolTipText(tip);
        form.add(field, c);
    }

    private JComponent buildButtons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("Generate");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> {
            try {
                result = collect();
                dispose();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "Invalid input", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancel.addActionListener(e -> { result = null; dispose(); });
        p.add(Box.createHorizontalGlue());
        p.add(ok);
        p.add(cancel);
        return p;
    }

    private GenerationParams collect() {
        boolean adv = advancedMode.isSelected();
        RedistrictingAlgorithm alg = adv
                ? (RedistrictingAlgorithm) algorithmCombo.getSelectedItem()
                : Algorithms.SIMPLE;
        return new GenerationParams(
                ((Number) districtsSpinner.getValue()).intValue(),
                ((Number) precinctsXSpinner.getValue()).intValue(),
                ((Number) precinctsYSpinner.getValue()).intValue(),
                ((Number) countiesXSpinner.getValue()).intValue(),
                ((Number) countiesYSpinner.getValue()).intValue(),
                adv ? biasSlider.getValue() : 0,
                adv ? countySlider.getValue() / 100.0 : 0.5,
                adv ? compactSlider.getValue() / 100.0 : 0.5,
                adv ? popTolSlider.getValue() / 1000.0 : 0.02,
                adv ? reliabilitySlider.getValue() / 100.0 : 0.3,
                ((Number) seedSpinner.getValue()).longValue(),
                alg.id());
    }

    private static JSlider labelledSlider(int min, int max, int value, int spacing) {
        JSlider s = new JSlider(SwingConstants.HORIZONTAL, min, max, value);
        s.setMajorTickSpacing(spacing);
        s.setMinorTickSpacing(spacing / 5);
        s.setPaintTicks(true);
        s.setPaintLabels(true);
        return s;
    }
}
