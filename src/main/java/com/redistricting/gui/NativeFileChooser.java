package com.redistricting.gui;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wrapper around {@link java.awt.FileDialog} that uses the OS-native file
 * picker — Windows Explorer on Windows, Finder on macOS, GTK on most Linux
 * desktops — instead of Swing's {@link javax.swing.JFileChooser}.
 *
 * <p>Filtering by extension is implemented client-side via
 * {@link FileDialog#setFilenameFilter} because Windows ignores the AWT
 * filter on save dialogs; we accept the file regardless and validate later.
 */
public final class NativeFileChooser {

    private NativeFileChooser() {}

    /** Show a native "open" dialog with the given title and accepted extensions. */
    public static Path showOpen(Frame owner, String title, String... extensions) {
        FileDialog fd = new FileDialog(owner, title, FileDialog.LOAD);
        applyFilter(fd, extensions);
        fd.setVisible(true);
        return resolve(fd);
    }

    /** Show a native "save" dialog with a default filename. */
    public static Path showSave(Frame owner, String title, String defaultName,
                                String... extensions) {
        FileDialog fd = new FileDialog(owner, title, FileDialog.SAVE);
        if (defaultName != null) fd.setFile(defaultName);
        applyFilter(fd, extensions);
        fd.setVisible(true);
        return resolve(fd);
    }

    private static void applyFilter(FileDialog fd, String[] extensions) {
        if (extensions == null || extensions.length == 0) return;
        Set<String> accepted = Arrays.stream(extensions)
                .map(e -> e.startsWith(".") ? e.toLowerCase(Locale.ROOT)
                                            : "." + e.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        fd.setFilenameFilter((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String ext : accepted) if (lower.endsWith(ext)) return true;
            return false;
        });
    }

    private static Path resolve(FileDialog fd) {
        String dir = fd.getDirectory();
        String file = fd.getFile();
        if (file == null) return null;
        return new File(dir == null ? "." : dir, file).toPath();
    }

    /**
     * Convenience for displaying a friendly, comma-separated extensions list
     * in dialog titles, e.g. {@code "(.geojson, .json, .csv)"}.
     */
    public static String formatExtensions(List<String> extensions) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < extensions.size(); i++) {
            if (i > 0) sb.append(", ");
            String e = extensions.get(i);
            sb.append(e.startsWith(".") ? e : "." + e);
        }
        return sb.append(")").toString();
    }
}
