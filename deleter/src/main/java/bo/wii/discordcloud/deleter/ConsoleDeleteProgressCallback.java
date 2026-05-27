package bo.wii.discordcloud.deleter;

import bo.wii.discordcloud.cli.AnsiColor;
import bo.wii.discordcloud.cli.ConsoleRenderer;
import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.services.delete.DeleteProgressCallback;

/**
 * Console delete progress callback.
 * Delegates all terminal rendering to {@link ConsoleRenderer}.
 */
public class ConsoleDeleteProgressCallback implements DeleteProgressCallback {

    private final ConsoleRenderer renderer = new ConsoleRenderer();

    private final int totalParts;
    private boolean deletePhaseFinished = false;

    public ConsoleDeleteProgressCallback(int totalParts) {
        this.totalParts = totalParts;
    }

    public void start() {
        // Use totalParts as the "bytes" scale so the bar tracks parts, not real bytes
        renderer.startBar(totalParts, totalParts, "Deleting");

        // Redirect Logger output through the renderer
        Logger.setOutputWriter(renderer::printMessage, renderer::printError);
    }

    @Override
    public void onLog(String message) {
        // Suppress messages already reflected in the bar header / onComplete
        if (message.startsWith("Deleting part ") ||
            message.startsWith("Part ") && message.contains("deleted successfully") ||
            message.startsWith("Deletion complete.") ||
            message.startsWith("Deleting: ") ||
            message.startsWith("Upload type:") ||
            message.startsWith("Total parts:")) {
            return;
        }

        renderer.printMessage(colorizeKeywords(message));
    }

    @Override
    public void onProgress(int current, int total) {
        if (!deletePhaseFinished) {
            renderer.updateBar(current, total, 0, current, total);
        }
    }

    @Override
    public void onError(String message) {
        if (renderer.isBarActive()) {
            renderer.clearBar();
        }
        renderer.printError(AnsiColor.error("ERROR: ", message));
    }

    @Override
    public void onComplete(int deleted, int total) {
        if (!deletePhaseFinished) {
            deletePhaseFinished = true;
            renderer.updateBar(deleted, total, 0, deleted, total);
            renderer.finalizeBar();
        }

        // Detach Logger from renderer
        Logger.setOutputWriter(null, null);

        renderer.println();
        renderer.println(deleted == total
                ? AnsiColor.greenBold("  Deletion complete")
                : AnsiColor.yellow("  Deletion complete with errors"));
        renderer.println(AnsiColor.label("  Deleted: ", deleted + " / " + total + " parts"));
        renderer.println();
    }

    public void close() {
        Logger.setOutputWriter(null, null);
        if (renderer.isBarActive()) {
            renderer.clearBar();
        }
        renderer.close();
    }

    private static String colorizeKeywords(String message) {
        if (message.startsWith("All parts deleted") || message.startsWith("Deletion complete")) {
            return AnsiColor.green(message);
        } else if (message.startsWith("ERROR") || message.startsWith("Failed") || message.startsWith("Could not")) {
            return AnsiColor.red(message);
        } else if (message.startsWith("Deleting:")) {
            return AnsiColor.yellow(message);
        }
        return message;
    }
}


