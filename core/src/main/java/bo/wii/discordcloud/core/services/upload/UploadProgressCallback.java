package bo.wii.discordcloud.core.services.upload;

/**
 * Interface for receiving upload progress updates
 */
public interface UploadProgressCallback {
    
    /**
     * Called when a log message should be displayed
     * @param message the log message
     */
    void onLog(String message);
    
    /**
     * Called when upload progress changes
     * @param current current part number (0-based)
     * @param total total number of parts
     */
    void onProgress(int current, int total);
    
    /**
     * Called when an error occurs
     * @param message error message
     */
    void onError(String message);
    
    /**
     * Called when upload is complete
     * @param structureFile the generated structure file (.dscl)
     */
    void onComplete(String structureFile);
}

