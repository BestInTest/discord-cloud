package bo.wii.discordcloud.downloader;

import bo.wii.discordcloud.cli.AnsiColor;
import bo.wii.discordcloud.cli.ConsoleRenderer;
import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.services.download.DownloadProgressCallback;
import bo.wii.discordcloud.core.utils.FileHelper;

/**
 * Console download progress callback.
 * Delegates all terminal rendering to {@link ConsoleRenderer}.
 */
public class ConsoleDownloadProgressCallback implements DownloadProgressCallback {

    private final ConsoleRenderer renderer = new ConsoleRenderer();

    private final long totalBytes;
    private final int totalParts;
    private long downloadStartTime;
    private boolean downloadPhaseFinished = false;

    public ConsoleDownloadProgressCallback(long totalBytes, int totalParts) {
        this.totalBytes = totalBytes;
        this.totalParts = totalParts;
    }

    public void start() {
        downloadStartTime = System.currentTimeMillis();
        renderer.startBar(totalBytes, totalParts, "Downloading");

        // Redirect Logger output through the renderer so debug logs
        // are printed above the progress bar instead of breaking it.
        Logger.setOutputWriter(renderer::printMessage, renderer::printError);
    }

    @Override
    public void onLog(String message) {
        // Messages fully handled by the progress bar or the header/onComplete
        if (message.startsWith("Downloading part ") ||
            message.startsWith("Downloading: ") ||
            message.startsWith("Total parts: ") ||
            (message.startsWith("Part ") && message.contains("downloaded successfully")) ||
            message.startsWith("Average download speed:") ||
            message.startsWith("Download complete.") ||
            message.startsWith("Saved to: ")) {
            return;
        }

        // "All parts downloaded" means the download loop is done.
        // Finalize the bar so subsequent messages print normally below it.
        if (!downloadPhaseFinished && message.startsWith("All parts downloaded")) {
            downloadPhaseFinished = true;
            renderer.finalizeBar();
        }

        renderer.printMessage(colorizeKeywords(message));
    }

    @Override
    public void onProgress(int current, int total) {
        // handled by onProgressWithSpeed
    }

    @Override
    public void onProgressWithSpeed(int current, int total, long downloadedBytes, long totalBytes, double speedBytesPerSecond) {
        if (!downloadPhaseFinished) {
            renderer.updateBar(downloadedBytes, totalBytes, speedBytesPerSecond, current, total);
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
    public void onComplete(String outputFile) {
        // Make sure bar is finalized
        if (renderer.isBarActive()) {
            renderer.finalizeBar();
        }

        // Detach Logger from renderer - logs will go straight to System.out/err
        Logger.setOutputWriter(null, null);

        long elapsed = System.currentTimeMillis() - downloadStartTime;
        double avgSpeed = elapsed > 0 ? (totalBytes * 1000.0) / elapsed : 0;

        renderer.println();
        renderer.println(AnsiColor.greenBold("Download complete"));
        renderer.println(AnsiColor.label("  File:      ", outputFile));
        renderer.println(AnsiColor.label("  Size:      ", FileHelper.formatFileSize(totalBytes)));
        renderer.println(AnsiColor.label("  Avg speed: ", FileHelper.formatSpeed(avgSpeed)));
        //TODO: dodać całkowity czas pobierania
        renderer.println();
    }

    public void close() {
        // Detach Logger from renderer
        Logger.setOutputWriter(null, null);

        if (renderer.isBarActive()) {
            renderer.clearBar();
        }
        renderer.close();
    }



    private static String colorizeKeywords(String message) {
        if (message.startsWith("File merged successfully") || message.startsWith("File integrity verified successfully")) {
            return AnsiColor.green(message);
        } else if (message.startsWith("ERROR") || message.startsWith("Failed") || message.startsWith("Could not")) {
            return AnsiColor.red(message);
        } else if (message.startsWith("Verifying") || message.startsWith("Merging") || message.startsWith("All parts")) {
            return AnsiColor.yellow(message);
        }
        return message;
    }
}
