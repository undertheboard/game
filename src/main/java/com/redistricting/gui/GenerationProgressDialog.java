package com.redistricting.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Modal dialog that shows an indeterminate progress bar (and an animated
 * status line) while a redistricting plan is being generated on a worker
 * thread.
 *
 * <p>The dialog is owner-modal and non-resizable; it fields a Cancel
 * button which simply hides the dialog and signals the caller via a flag.
 * The caller is responsible for honouring cancellation (the in-tree
 * generators don't currently expose interruption hooks, so the worker
 * thread will run to completion either way — but the user gets immediate
 * UI feedback that they don't have to wait).
 */
public final class GenerationProgressDialog extends JDialog {

    private final JProgressBar bar = new JProgressBar();
    private final JLabel detail = new JLabel("Preparing…");
    private volatile boolean cancelled = false;

    public GenerationProgressDialog(Frame owner, String title) {
        super(owner, title, true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { cancelled = true; }
        });

        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(420, 18));
        detail.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(16, 18, 8, 18));
        center.add(detail, BorderLayout.NORTH);
        center.add(bar, BorderLayout.CENTER);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> { cancelled = true; setVisible(false); });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancel);

        setLayout(new BorderLayout());
        add(center, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Update the status label (safe to call from any thread). */
    public void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> detail.setText(msg));
    }

    /** Switch to a determinate progress bar with the given range. */
    public void setRange(int min, int max) {
        SwingUtilities.invokeLater(() -> {
            bar.setIndeterminate(false);
            bar.setMinimum(min);
            bar.setMaximum(max);
            bar.setStringPainted(true);
        });
    }

    /** Set the progress value (only meaningful after {@link #setRange}). */
    public void setValue(int value) {
        SwingUtilities.invokeLater(() -> bar.setValue(value));
    }

    public boolean isCancelled() { return cancelled; }

    /** Hide and dispose the dialog (safe to call from any thread). */
    public void finish() {
        SwingUtilities.invokeLater(() -> { setVisible(false); dispose(); });
    }
}
