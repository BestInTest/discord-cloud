package bo.wii.discordcloud.core.services.delete;

/**
 * Interface for receiving delete progress updates
 */
public interface DeleteProgressCallback {

    /**
     * Called when a log message should be displayed
     * @param message the log message
     */
    void onLog(String message);

    /**
     * Called when delete progress changes
     * @param current number of parts deleted so far
     * @param total total number of parts
     */
    void onProgress(int current, int total);

    /**
     * Called when an error occurs
     * @param message error message
     */
    void onError(String message);

    /**
     * Called when deletion is complete
     * @param deleted number of successfully deleted parts
     * @param total total number of parts
     */
    void onComplete(int deleted, int total);
}

