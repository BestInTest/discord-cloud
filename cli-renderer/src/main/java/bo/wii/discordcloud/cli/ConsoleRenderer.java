package bo.wii.discordcloud.cli;

import bo.wii.discordcloud.core.utils.FileHelper;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Terminal rendering engine
 * <p>
 * Uses JLine and JAnsi for cross-platform terminal support.
 * Manages a single-line progress bar that stays on the last line of the terminal.
 * Messages can be printed above the bar without breaking its position.
 */
public class ConsoleRenderer implements AutoCloseable {

    private static final int MIN_BAR_WIDTH = 10;
    private static final int BAR_OVERHEAD = 60; // approximate chars outside the bar (label, size, speed, part)

    private final Terminal terminal;
    private final PrintWriter writer;
    private final boolean ansiSupported;

    private boolean barActive = false;

    // Current bar state
    private String barLabel = "Progress";
    private double fraction = 0;
    private long downloadedBytes = 0;
    private long totalBytes = 0;
    private double speedBytesPerSec = 0;
    private int currentPart = 0;
    private int totalParts = 0;

    public ConsoleRenderer() {
        Terminal term;
        try {
            term = TerminalBuilder.builder()
                    .system(true)
                    .dumb(false)
                    .jansi(true)
                    .build();
        } catch (IOException e) {
            // Fallback to dumb terminal
            try {
                term = TerminalBuilder.builder().dumb(true).build();
            } catch (IOException ex) {
                throw new RuntimeException("Failed to create terminal", ex);
            }
        }
        this.terminal = term;
        this.writer = terminal.writer();
        this.ansiSupported = !Terminal.TYPE_DUMB.equals(terminal.getType());
    }


    // Bar lifecycle

    /**
     * Activate the progress bar with specified label (e.g. "Downloading").
     * It will be drawn on the current line.
     */
    public void startBar(long totalBytes, int totalParts, String label) {
        this.totalBytes = totalBytes;
        this.totalParts = totalParts;
        this.barLabel = label;
        barActive = true;
        drawBar();
    }

    /**
     * Update bar state and redraw.
     */
    public void updateBar(long downloadedBytes, long totalBytes, double speedBytesPerSec, int currentPart, int totalParts) {
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
        this.speedBytesPerSec = speedBytesPerSec;
        this.currentPart = currentPart;
        this.totalParts = totalParts;
        this.fraction = totalBytes > 0 ? (double) downloadedBytes / totalBytes : 0;
        drawBar();
    }

    /**
     * Draw it one last time and move to the next line.
     * After this call the bar is no longer active and messages print normally.
     */
    public void finalizeBar() {
        if (!barActive) return;
        drawBar();
        writer.println();
        writer.flush();
        barActive = false;
    }

    /**
     * Clear the bar line completely without printing anything else.
     */
    public void clearBar() {
        if (!barActive) return;
        if (ansiSupported) {
            writer.print(AnsiColor.clearLine()); // Clear line and return cursor to start
        } else {
            writer.println();
        }
        writer.flush();
        barActive = false;
    }

    public boolean isBarActive() {
        return barActive;
    }


    // Printing

    /**
     * Print a message. If the bar is active, the message is printed above
     * the bar and the bar is redrawn on the last line.
     */
    public void printMessage(String message) {
        if (barActive) {
            if (ansiSupported) {
                writer.print(AnsiColor.clearLine()); // wipe bar line
            }
            writer.println(message); // print message above the bar
            writer.flush();
            drawBar(); // redraw bar on the last line
        } else {
            writer.println(message);
            writer.flush();
        }
    }

    /**
     * Print to stderr. Clears the bar first if active, then redraws.
     */
    public void printError(String message) {
        if (barActive && ansiSupported) {
            writer.print(AnsiColor.clearLine()); // Clear bar line before printing error
            writer.flush();
        }
        System.err.println(message);
        if (barActive) {
            drawBar();
        }
    }

    /**
     * Simple println.
     */
    public void println(String message) {
        writer.println(message);
        writer.flush();
    }

    public void println() {
        writer.println();
        writer.flush();
    }

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException ignored) {
        }
    }


    // Internal rendering

    private int getBarWidth() {
        int termWidth = terminal.getWidth();
        if (termWidth <= 0) termWidth = 80; // fallback
        int available = termWidth - BAR_OVERHEAD;
        return Math.max(MIN_BAR_WIDTH, Math.min(available, 50));
    }

    private void drawBar() {
        if (!ansiSupported) {
            // Dumb terminal (fallback to simple text)
            String percent = String.format("%.1f%%", fraction * 100);
            writer.print("\r  " + barLabel + " " + percent
                    + "  " + FileHelper.formatFileSize(downloadedBytes) + "/" + FileHelper.formatFileSize(totalBytes)
                    + "  [" + currentPart + "/" + totalParts + "]");
            writer.flush();
            return;
        }

        int barWidth = getBarWidth();
        int filled = (int) (barWidth * fraction);
        int empty = barWidth - filled;

        AttributedStringBuilder sb = new AttributedStringBuilder();

        // Label
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
        sb.append("  ").append(barLabel).append(" ");
        sb.style(AttributedStyle.DEFAULT);

        // Percent
        sb.append(String.format("%5.1f%%", fraction * 100));
        sb.append("  ");

        // Bar
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
        sb.append("█".repeat(Math.max(0, filled)));
        sb.style(AttributedStyle.DEFAULT);
        sb.append("░".repeat(Math.max(0, empty)));

        // Size
        sb.append("  ");
        sb.append(FileHelper.formatFileSize(downloadedBytes));
        sb.append("/");
        sb.append(FileHelper.formatFileSize(totalBytes));

        // Speed
        if (speedBytesPerSec > 0) {
            sb.append("  ");
            sb.style(AttributedStyle.DEFAULT.bold());
            sb.append(FileHelper.formatSpeed(speedBytesPerSec));
            sb.style(AttributedStyle.DEFAULT);
        }

        // Part counter
        sb.append("  [").append(String.valueOf(currentPart)).append("/").append(String.valueOf(totalParts)).append("]");

        writer.print("\r" + sb.toAnsi(terminal));
        writer.flush();
    }
}
