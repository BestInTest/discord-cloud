package bo.wii.discordcloud.core.services.download;

/**
 * Interface for receiving download progress updates
 */
public interface DownloadProgressCallback {
    
    /**
     * Called when a log message should be displayed
     * @param message the log message
     */
    void onLog(String message);
    
    /**
     * Called when download progress changes
     * @param current current part number (0-based)
     * @param total total number of parts
     */
    void onProgress(int current, int total);
    
    /**
     * Called when download progress changes with speed information
     * @param current current part number (0-based)
     * @param total total number of parts
     * @param downloadedBytes total bytes downloaded so far
     * @param totalBytes total file size in bytes
     * @param speedBytesPerSecond current download speed in bytes per second
     */
    default void onProgressWithSpeed(int current, int total, long downloadedBytes, long totalBytes, double speedBytesPerSecond) {
        onProgress(current, total);
    }

    /**
     * Called when an error occurs
     * @param message error message
     */
    void onError(String message);
    
    /**
     * Called when download is complete
     * @param outputFile the final merged file
     */
    void onComplete(String outputFile);
}

