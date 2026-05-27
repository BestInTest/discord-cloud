package bo.wii.discordcloud.cli;

import org.jline.jansi.Ansi;

import static org.jline.jansi.Ansi.ansi;

public final class AnsiColor {

    // Single-color wrappers

    public static String green(String text) {
        return ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
    }

    public static String red(String text) {
        return ansi().fg(Ansi.Color.RED).a(text).reset().toString();
    }

    public static String yellow(String text) {
        return ansi().fg(Ansi.Color.YELLOW).a(text).reset().toString();
    }

    public static String cyan(String text) {
        return ansi().fg(Ansi.Color.CYAN).a(text).reset().toString();
    }

    public static String dim(String text) {
        return ansi().a(Ansi.Attribute.INTENSITY_FAINT).a(text).reset().toString();
    }

    // Bold variants

    public static String bold(String text) {
        return ansi().bold().a(text).reset().toString();
    }

    public static String greenBold(String text) {
        return ansi().fg(Ansi.Color.GREEN).bold().a(text).reset().toString();
    }

    public static String redBold(String text) {
        return ansi().fg(Ansi.Color.RED).bold().a(text).reset().toString();
    }

    // Combined (color + reset in one call)

    /**
     * Red bold prefix + red message body.
     * E.g. {@code error("ERROR: ", "something broke")} -> red bold prefix, then red body.
     */
    public static String error(String prefix, String body) {
        return ansi().fg(Ansi.Color.RED).bold().a(prefix).boldOff().a(body).reset().toString();
    }

    /**
     * Green label + default-color value.
     * Useful for summary lines like "File: /path/to/file"
     */
    public static String label(String label, String value) {
        return ansi().fg(Ansi.Color.GREEN).a(label).reset().a(value).toString();
    }


    // For terminal control

    /**
     * ANSI sequence to erase the entire current line and return the cursor to column 0.
     */
    public static String clearLine() {
        return ansi().eraseLine(Ansi.Erase.ALL).cursorToColumn(0).toString();
    }
}

