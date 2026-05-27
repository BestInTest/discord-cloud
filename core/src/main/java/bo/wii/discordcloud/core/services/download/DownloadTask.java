package bo.wii.discordcloud.core.services.download;

import bo.wii.discordcloud.core.client.LinkRefresher;
import bo.wii.discordcloud.core.structure.ChunkFileInfo;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.utils.FileHashCalculator;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.core.utils.FileMerger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Task for downloading files with progress tracking
 */
public class DownloadTask {
    
    private final FileStruct structure;
    private final LinkRefresher linkRefresher;
    private final DownloadProgressCallback callback;
    private final boolean prefetchEnabled;
    private final boolean checkPartHash;
    private volatile boolean stopped = false;

    /**
     * DownloadTask with custom LinkRefresher
     */
    public DownloadTask(FileStruct structure, LinkRefresher linkRefresher, DownloadProgressCallback callback) {
        this(structure, linkRefresher, callback, true, false);
    }

    /**
     * DownloadTask with custom LinkRefresher and prefetch option
     */
    public DownloadTask(FileStruct structure, LinkRefresher linkRefresher, DownloadProgressCallback callback, boolean prefetchEnabled, boolean checkPartHash) {
        this.structure = structure;
        this.linkRefresher = linkRefresher;
        this.callback = callback;
        this.prefetchEnabled = prefetchEnabled;
        this.checkPartHash = checkPartHash;
    }
    
    /**
     * Execute the download task
     * @return true if successful, false otherwise
     */
    public boolean execute() {
        try (DownloaderService downloader = new DownloaderService(structure, linkRefresher, prefetchEnabled)) {
            callback.onLog("Downloading: " + structure.getOriginalFileName());
            callback.onLog("Total parts: " + structure.getParts().size());
            
            String tempDownloadDir = ".temp/downloads/" + structure.getOriginalFileName() + "/";
            int partIndex = 0;
            int totalParts = structure.getParts().size();
            long totalBytes = structure.getFileSize();
            long downloadedBytes = 0;

            // For calculating download speed
            long startTime = System.currentTimeMillis();
            long lastUpdateTime = startTime;
            long lastDownloadedBytes = 0;

            /*
             TODO:
              Jeśli będzie problem z pamięcią to można spróbować dać tutaj
              try (DownloaderService downloader = new DownloaderService(structure, webhookUrl, prefetchEnabled)) {
              i zakończyć go zaraz po pętli pobierającej części (bez robienia catch'a)
             */
            // Downloading all parts
            while (partIndex < totalParts && !stopped) {
                if (Thread.currentThread().isInterrupted()) {
                    callback.onLog("Download interrupted.");
                    return false;
                }
                
                int currentPart = partIndex + 1;
                callback.onLog("Downloading part " + currentPart + "/" + totalParts);
                
                File filePart = downloader.downloadPart(partIndex, tempDownloadDir);
                
                if (filePart != null) {
                    // Verify part hash if enabled and available
                    //TODO: dodać sprawdzanie czy hash jest dostępny, w przyszłości może być tak że nie będzie hashów dla części, wtedy powinno to być pomijane
                    if (checkPartHash) {
                        List<ChunkFileInfo> partsList = new ArrayList<>(structure.getParts());
                        ChunkFileInfo expectedPart = partsList.get(partIndex);
                        String expectedHash = expectedPart.getSha256Hash();

                        if (expectedHash != null && !expectedHash.isEmpty()) {
                            String actualHash = FileHashCalculator.getFileHash(filePart);

                            if (!expectedHash.equalsIgnoreCase(actualHash)) {
                                callback.onLog("Hash verification failed for part " + currentPart);
                                callback.onLog("Expected: " + expectedHash);
                                callback.onLog("Actual: " + actualHash);
                                callback.onError("Download integrity check failed at part " + currentPart);
                                filePart.delete();
                                return false;
                            }
                        }
                    }

                    // Update downloaded bytes
                    downloadedBytes += filePart.length();
                    partIndex++;

                    // Calculate download speed
                    long currentTime = System.currentTimeMillis();
                    long timeDiff = currentTime - lastUpdateTime;
                    long bytesDiff = downloadedBytes - lastDownloadedBytes;
                    double speedBytesPerSecond = (bytesDiff * 1000.0) / timeDiff;

                    // callback with download speed
                    callback.onProgressWithSpeed(partIndex, totalParts, downloadedBytes, totalBytes, speedBytesPerSecond);

                    // Format speed
                    String speedFormatted = FileHelper.formatSpeed(speedBytesPerSecond);
                    String progressPercent = String.format("%.1f%%", (downloadedBytes * 100.0) / totalBytes);
                    callback.onLog("Part " + currentPart + "/" + totalParts + " downloaded successfully - " + progressPercent + " - " + speedFormatted);

                    lastUpdateTime = currentTime;
                    lastDownloadedBytes = downloadedBytes;

                } else {
                    callback.onError("Download failed at part " + currentPart);
                    return false;
                }
            }
            
            // Calculate avg download speed
            long totalTime = System.currentTimeMillis() - startTime;
            if (totalTime > 0 && !stopped) {
                double averageSpeed = (downloadedBytes * 1000.0) / totalTime;
                callback.onLog("Average download speed: " + FileHelper.formatSpeed(averageSpeed));
            }

            if (stopped) {
                callback.onLog("Download cancelled.");
                return false;
            }
            
            // Merging chunks
            callback.onLog("All parts downloaded. Merging files...");
            
            try {
                File finalFile = new File("downloads/");
                if (!finalFile.mkdirs() && !finalFile.exists()) {
                    callback.onError("Could not create downloads directory");
                    return false;
                }
                finalFile = new File("downloads/" + structure.getOriginalFileName());
                FileMerger.mergeFiles(tempDownloadDir, finalFile);
                
                callback.onLog("File merged successfully.");
                callback.onLog("Saved to: " + finalFile.getAbsolutePath());

                // Hash check after merging
                String expectedFileHash = structure.getSha256Hash();
                if (expectedFileHash != null && !expectedFileHash.isEmpty()) {
                    callback.onLog("Verifying file integrity...");
                    String actualFileHash = FileHashCalculator.getFileHash(finalFile);
                    if (!expectedFileHash.equalsIgnoreCase(actualFileHash)) {
                        callback.onLog("File hash verification failed!");
                        callback.onLog("Expected: " + expectedFileHash);
                        callback.onLog("Actual: " + actualFileHash);
                        callback.onError("File integrity check failed - downloaded file may be corrupted.");
                        finalFile.delete();
                        return false;
                    }
                    callback.onLog("File integrity verified successfully.");
                }

                callback.onLog("Download complete.");
                callback.onComplete(finalFile.getAbsolutePath());
                return true;
            } catch (IOException e) {
                callback.onError("Failed to merge files: " + e.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            callback.onError(e.getMessage());
            return false;
        }
    }

    /**
     * Stop the download task
     */
    public void stop() {
        stopped = true;
    }
    
    public FileStruct getStructure() {
        return structure;
    }
}
