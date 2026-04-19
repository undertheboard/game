package com.redistricting.gui;

import com.redistricting.ai.GenerationParams;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Modal dialog that gathers {@link GenerationParams} from the user. Returns
 * the chosen params (or {@code null} if cancelled) via {@link #showDialog}.
 */
public final class GenerateDialog extends JDialog {

    private final JSpinner districtsSpinner =
            new JSpinner(new SpinnerNumberModel(8, 2, 50, 1));
    private final JSpinner precinctsXSpinner =
            new JSpinner(new SpinnerNumberModel(20, 4, 80, 1));
    private final JSpinner precinctsYSpinner =
            new JSpinner(new SpinnerNumberModel(20, 4, 80, 1));
    private final JSpinner countiesXSpinner =
            new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
    private final JSpinner countiesYSpinner =
            new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));

    private final JSlider biasSlider     = labelledSlider(-100, 100, 0, 25);
    private final JSlider countySlider   = labelledSlider(0, 100, 50, 25);
    private final JSlider compactSlider  = labelledSlider(0, 100, 50, 25);
    private final JSlider popTolSlider   = labelledSlider(0, 100, 20, 25); // ‰ of ideal
    private final JSlider reliabilitySlider = labelledSlider(0, 100, 50, 25);

    private final JSpinner seedSpinner =
            new JSpinner(new SpinnerNumberModel(System.currentTimeMillis() & 0xFFFFFFFL,
                    0L, Long.MAX_VALUE, 1L));

    private GenerationParams result;

    private GenerateDialog(Frame owner) {
        super(owner, "Generate Plan", true);
        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        pack();
        setMinimumSize(new Dimension(560, getHeight()));
        setLocationRelativeTo(owner);
    }

    public static GenerationParams showDialog(Frame owner) {
        GenerateDialog d = new GenerateDialog(owner);
        d.setVisible(true);
        return d.result;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(form, c, row++, "Districts:", districtsSpinner,
                "Number of seats to draw");
        addRow(form, c, row++, "Precincts (X × Y):", twin(precinctsXSpinner, precinctsYSpinner),
                "Resolution of the synthetic precinct grid");
        addRow(form, c, row++, "Counties (X × Y):", twin(countiesXSpinner, countiesYSpinner),
                "How many counties the precincts are grouped into");

        addRow(form, c, row++, "Partisan bias  (R+100 ⇄ D+100):", biasSlider,
                "Negative = Republican-favoring gerrymander, positive = Democratic-favoring");
        addRow(form, c, row++, "County-line adherence (%):", countySlider,
                "Penalty for splitting counties between districts");
        addRow(form, c, row++, "Compactness (%):", compactSlider,
                "Weight on geometric compactness in the growth heuristic");
        addRow(form, c, row++, "Population tolerance (‰ of ideal):", popTolSlider,
                "Allowed |population deviation| from the ideal district size");
        addRow(form, c, row++, "Reliability (% — # restarts):", reliabilitySlider,
                "Higher = more independent attempts; the best is kept");
        addRow(form, c, row++, "Random seed:", seedSpinner,
                "Same seed + same params reproduces the same map");
        return form;
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
                JLabel msg = new JLabel(ex.getMessage());
                javax.swing.JOptionPane.showMessageDialog(this, msg, "Invalid input",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        });
        cancel.addActionListener(e -> { result = null; dispose(); });
        p.add(Box.createHorizontalGlue());
        p.add(ok);
        p.add(cancel);
        return p;
    }

    private GenerationParams collect() {
        return new GenerationParams(
                ((Number) districtsSpinner.getValue()).intValue(),
                ((Number) precinctsXSpinner.getValue()).intValue(),
                ((Number) precinctsYSpinner.getValue()).intValue(),
                ((Number) countiesXSpinner.getValue()).intValue(),
                ((Number) countiesYSpinner.getValue()).intValue(),
                biasSlider.getValue(),
                countySlider.getValue() / 100.0,
                compactSlider.getValue() / 100.0,
                popTolSlider.getValue() / 1000.0, // slider is ‰
                reliabilitySlider.getValue() / 100.0,
                ((Number) seedSpinner.getValue()).longValue());
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
