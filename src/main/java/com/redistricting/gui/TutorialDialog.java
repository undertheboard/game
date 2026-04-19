package com.redistricting.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Multi-step in-app tutorial. Shown automatically on first launch (controlled
 * by a {@link Preferences} flag) and re-openable from <em>Help → Tutorial</em>.
 *
 * <p>Each step has a heading and an HTML body so the user can follow along
 * without leaving the app.
 */
public final class TutorialDialog extends JDialog {

    private static final String PREF_KEY = "tutorial.shown.v1";

    private final List<Step> steps = List.of(
            new Step("1. Welcome",
                    "<h2>Redistricting Fairness Analyzer</h2>"
                    + "<p>This app loads redistricting plans, displays them, "
                    + "evaluates their fairness with an AI, and can even "
                    + "generate plans of its own.</p>"
                    + "<p>This short tour shows you how to set everything up. "
                    + "You can re-open it any time from "
                    + "<b>Help &rarr; Tutorial</b>.</p>"),

            new Step("2. The map canvas",
                    "<h2>Navigating the canvas</h2>"
                    + "<ul>"
                    + "<li><b>Pan</b> — click and drag.</li>"
                    + "<li><b>Zoom</b> — scroll the mouse wheel (zooms about the cursor).</li>"
                    + "<li><b>Reset view</b> — <i>File &rarr; Reset View</i>.</li>"
                    + "<li><b>Inspect a precinct</b> — hover over it to see "
                    + "its district, population, and partisan vote counts.</li>"
                    + "</ul>"
                    + "<p>The legend in the upper-left lists every district "
                    + "with its colour and population total.</p>"),

            new Step("3. Importing from Dave's Redistricting",
                    "<h2>Bring in a real plan</h2>"
                    + "<p>This app reads files exported from "
                    + "<b>Dave's Redistricting App (DRA)</b> directly &mdash; "
                    + "no conversion needed.</p>"
                    + "<ol>"
                    + "<li>Open your plan in DRA and click "
                    + "<i>Export Map to a File</i>.</li>"
                    + "<li>Pick one of:"
                    + "  <ul>"
                    + "    <li><b>District Shapes (.geojson)</b> &mdash; geometry + per-district stats.</li>"
                    + "    <li><b>Map Archive (.json)</b> &mdash; the full roundtrip file.</li>"
                    + "    <li><b>District Data (.csv)</b> &mdash; enriches an already-loaded plan with population &amp; vote totals.</li>"
                    + "  </ul></li>"
                    + "<li>Save it locally, then in this app choose "
                    + "<b>File &rarr; Import from Dave's Redistricting&hellip;</b> "
                    + "and pick the file. The format is auto-detected.</li>"
                    + "</ol>"),

            new Step("4. Analysing fairness",
                    "<h2>The AI fairness report</h2>"
                    + "<p>Choose <b>AI &rarr; Analyze Fairness</b>. You'll see:</p>"
                    + "<ul>"
                    + "<li><b>Population deviation</b> &mdash; max |district pop &minus; ideal| / ideal. "
                    + "Lower is fairer (0% = perfectly equal districts).</li>"
                    + "<li><b>Compactness</b> &mdash; average Polsby-Popper score "
                    + "(4&pi;A / P&sup2;). 1.0 = circle, near 0 = sliver.</li>"
                    + "<li><b>Efficiency gap</b> &mdash; ratio of wasted votes between parties. "
                    + "0% is balanced; |gap| &ge; 7% is the common gerrymander threshold.</li>"
                    + "<li><b>Combined unfairness</b> &mdash; weighted total used by the optimiser.</li>"
                    + "</ul>"),

            new Step("5. Optimising a plan",
                    "<h2>Let the AI improve a plan</h2>"
                    + "<p>Choose <b>AI &rarr; Optimize Fairness</b>. The optimiser "
                    + "swaps boundary precincts between adjacent districts, "
                    + "keeping a swap only when it lowers the combined unfairness.</p>"
                    + "<p><i>Note:</i> this requires a precinct-level plan. "
                    + "DRA's <i>District Shapes</i> export only contains one shape "
                    + "per district, so to use the optimiser either import a "
                    + "precinct-level plan or use <b>Generate Plan&hellip;</b> below.</p>"),

            new Step("6. Generating your own plan",
                    "<h2>Generate Plan&hellip; &mdash; the big slider panel</h2>"
                    + "<p>Choose <b>AI &rarr; Generate Plan&hellip;</b> to build a "
                    + "synthetic state and let the AI draw districts to your spec. "
                    + "Every knob:</p>"
                    + "<ul>"
                    + "<li><b>Districts</b> &mdash; number of seats.</li>"
                    + "<li><b>Precincts X &times; Y</b> &mdash; resolution of the synthetic precinct grid.</li>"
                    + "<li><b>Counties X &times; Y</b> &mdash; how those precincts are grouped into counties.</li>"
                    + "<li><b>Partisan bias  R+100 &hArr; D+100</b> &mdash; "
                    + "negative values gerrymander toward Republicans, positive toward Democrats, 0 is proportional.</li>"
                    + "<li><b>County-line adherence</b> &mdash; how strongly the AI avoids splitting counties.</li>"
                    + "<li><b>Compactness</b> &mdash; weight on geometric compactness while growing districts.</li>"
                    + "<li><b>Population tolerance (&permil;)</b> &mdash; allowed deviation from the ideal district size.</li>"
                    + "<li><b>Reliability</b> &mdash; how many independent attempts the AI runs (best is kept).</li>"
                    + "<li><b>Random seed</b> &mdash; same params + same seed reproduces the same plan.</li>"
                    + "</ul>"
                    + "<p>Click <b>Generate</b> &mdash; the new plan appears on the canvas. "
                    + "Run <b>Analyze Fairness</b> to see how close the result hit your bias target.</p>"),

            new Step("7. You're all set",
                    "<h2>That's the whole tour</h2>"
                    + "<p>Recap of the typical workflow:</p>"
                    + "<ol>"
                    + "<li><b>Import</b> a plan from DRA <i>or</i> <b>Generate</b> one.</li>"
                    + "<li><b>Analyze</b> its fairness.</li>"
                    + "<li><b>Optimize</b> (precinct-level plans) or tweak the generator sliders.</li>"
                    + "</ol>"
                    + "<p>You can re-open this tutorial any time from "
                    + "<b>Help &rarr; Tutorial</b>. Have fun!</p>")
    );

    private int index = 0;
    private final JLabel heading = new JLabel("", SwingConstants.LEFT);
    private final JEditorPane body = new JEditorPane("text/html", "");
    private final JButton backBtn = new JButton("Back");
    private final JButton nextBtn = new JButton("Next");
    private final JButton closeBtn = new JButton("Close");
    private final JLabel progress = new JLabel("");
    private final JCheckBox dontShow = new JCheckBox("Don't show on startup");

    private TutorialDialog(Frame owner) {
        super(owner, "Tutorial", true);
        setLayout(new BorderLayout());

        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        heading.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));

        body.setEditable(false);
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setPreferredSize(new Dimension(640, 360));
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));

        JPanel top = new JPanel(new BorderLayout());
        top.add(heading, BorderLayout.WEST);
        top.add(progress, BorderLayout.EAST);
        progress.setBorder(BorderFactory.createEmptyBorder(16, 16, 4, 16));

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        backBtn.addActionListener(e -> { if (index > 0) { index--; render(); } });
        nextBtn.addActionListener(e -> {
            if (index < steps.size() - 1) { index++; render(); }
            else dispose();
        });
        closeBtn.addActionListener(e -> dispose());

        render();
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildButtons() {
        JPanel p = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(dontShow);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(backBtn);
        right.add(nextBtn);
        right.add(closeBtn);
        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private void render() {
        Step s = steps.get(index);
        heading.setText(s.title);
        body.setText("<html><body style='font-family:sans-serif; font-size:11px;'>"
                + s.htmlBody + "</body></html>");
        body.setCaretPosition(0);
        progress.setText("Step " + (index + 1) + " of " + steps.size());
        backBtn.setEnabled(index > 0);
        nextBtn.setText(index == steps.size() - 1 ? "Finish" : "Next");
    }

    @Override
    public void dispose() {
        if (dontShow.isSelected()) {
            Preferences.userNodeForPackage(TutorialDialog.class).putBoolean(PREF_KEY, true);
        }
        super.dispose();
    }

    /** Open the tutorial unconditionally. */
    public static void show(Frame owner) {
        new TutorialDialog(owner).setVisible(true);
    }

    /** Open the tutorial only if the user hasn't dismissed it before. */
    public static void showOnFirstLaunch(Frame owner) {
        Preferences prefs = Preferences.userNodeForPackage(TutorialDialog.class);
        if (!prefs.getBoolean(PREF_KEY, false)) {
            show(owner);
        }
    }

    private record Step(String title, String htmlBody) {}
}
