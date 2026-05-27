package bo.wii.discordcloud.uploader;

import bo.wii.discordcloud.cli.AnsiColor;
import bo.wii.discordcloud.cli.ConsoleRenderer;
import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.services.upload.UploadProgressCallback;
import bo.wii.discordcloud.core.utils.FileHelper;

/**
 * Console upload progress callback.
 * Delegates all terminal rendering to {@link ConsoleRenderer}.
 */
public class ConsoleUploadProgressCallback implements UploadProgressCallback {

    private final ConsoleRenderer renderer = new ConsoleRenderer();

    private final long totalBytes;
    private final int totalParts;
    private final int chunkSize;
    private long uploadStartTime;
    private boolean uploadPhaseFinished = false;

    public ConsoleUploadProgressCallback(long totalBytes, int totalParts, int chunkSize) {
        this.totalBytes = totalBytes;
        this.totalParts = totalParts;
        this.chunkSize = chunkSize;
    }

    public void start() {
        uploadStartTime = System.currentTimeMillis();
        renderer.startBar(totalBytes, totalParts, "Uploading");

        // Redirect Logger output through the renderer so debug logs
        // are printed above the progress bar instead of breaking it.
        Logger.setOutputWriter(renderer::printMessage, renderer::printError);
    }

    @Override
    public void onLog(String message) {
        // Messages fully handled by the progress bar or the header/onComplete
        if (message.startsWith("Starting upload") ||
            message.startsWith("File: ") ||
            message.startsWith("Size: ") ||
            message.startsWith("Chunk size: ") ||
            message.startsWith("Total parts to upload: ") ||
            message.startsWith("Uploading part ") ||
            message.startsWith("Part ") ||
            message.startsWith("Upload complete.") ||
            message.startsWith("Structure file: ")) {
            return;
        }

        // "Generating thumbnail" means upload loop is done - finalize the bar
        if (!uploadPhaseFinished && message.startsWith("Generating thumbnail")) {
            uploadPhaseFinished = true;
            renderer.finalizeBar();
        }

        renderer.printMessage(colorizeKeywords(message));
    }

    @Override
    public void onProgress(int current, int total) {
        if (!uploadPhaseFinished) {
            long estimatedUploaded = Math.min((long) current * chunkSize, totalBytes);
            long elapsed = System.currentTimeMillis() - uploadStartTime;
            double speed = elapsed > 0 ? (estimatedUploaded * 1000.0) / elapsed : 0;
            renderer.updateBar(estimatedUploaded, totalBytes, speed, current, total);
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
    public void onComplete(String structureFile) {
        // Make sure bar is finalized
        if (renderer.isBarActive()) {
            renderer.finalizeBar();
        }

        // Detach Logger from renderer
        Logger.setOutputWriter(null, null);

        long elapsed = System.currentTimeMillis() - uploadStartTime;
        double avgSpeed = elapsed > 0 ? (totalBytes * 1000.0) / elapsed : 0;

        renderer.println();
        renderer.println(AnsiColor.greenBold("Upload Complete!"));
        renderer.println(AnsiColor.label("  File:      ", structureFile));
        renderer.println(AnsiColor.label("  Size:      ", FileHelper.formatFileSize(totalBytes)));
        renderer.println(AnsiColor.label("  Avg speed: ", FileHelper.formatSpeed(avgSpeed)));
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
        if (message.startsWith("ERROR") || message.startsWith("FATAL")) {
            return AnsiColor.red(message);
        } else if (message.startsWith("Generating") ||
                message.startsWith("Resuming") ||
                message.startsWith("Already uploaded") ||
                message.startsWith("Found existing") ||
                message.startsWith("Starting ")) {
            return AnsiColor.yellow(message);
        }
        return message;
    }
}


