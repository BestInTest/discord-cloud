package bo.wii.discordcloud.core.services.upload;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.exceptions.AuthorizationException;
import bo.wii.discordcloud.core.exceptions.FileMismatchException;
import bo.wii.discordcloud.core.exceptions.FileTooLargeException;
import bo.wii.discordcloud.core.exceptions.UploadException;
import bo.wii.discordcloud.core.services.ThumbnailService;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.thumbnail.api.ThumbnailResolution;

import java.io.File;
import java.io.IOException;

/**
 * Task for uploading files with progress tracking
 */
public class UploadTask {
    
    private final File file;
    private final String webhookUrl;
    private final int chunkSize;
    private final UploadProgressCallback callback;
    private volatile boolean stopped = false;
    private ThumbnailResolution thumbnailResolution = ThumbnailService.DEFAULT_RESOLUTION;
    private int thumbnailQuality = ThumbnailService.DEFAULT_QUALITY;

    public UploadTask(File file, String webhookUrl, int chunkSize, UploadProgressCallback callback) {
        this.file = file;
        this.webhookUrl = webhookUrl;
        this.chunkSize = chunkSize;
        this.callback = callback;
    }

    /**
     * Set the thumbnail resolution to use when generating thumbnails
     * @param resolution The desired resolution
     */
    public void setThumbnailResolution(ThumbnailResolution resolution) {
        this.thumbnailResolution = resolution;
    }

    /**
     * Set the thumbnail quality to use when generating thumbnails
     * @param quality The desired quality (50-100, where 100 is the best quality)
     */
    public void setThumbnailQuality(int quality) {
        this.thumbnailQuality = quality;
    }

    /**
     * Execute the upload task
     * @return true if successful, false otherwise
     */
    public boolean execute() {
        try {
            callback.onLog("Starting upload process...");
            callback.onLog("File: " + file.getName());
            callback.onLog("Size: " + FileHelper.formatFileSize(file.length()));
            
            // Check if existing structure file exists (for resuming)
            FileStruct existingStruct = getExistingStructure();
            
            UploaderService uploader = new UploaderService(webhookUrl, chunkSize);
            
            if (existingStruct == null) {
                // New upload
                callback.onLog("Starting new upload...");
                uploadNew(uploader);
            } else {
                // Resume upload
                callback.onLog("Found existing structure file, resuming upload...");
                uploadResume(uploader, existingStruct);
            }
            
            if (stopped) {
                callback.onLog("Upload cancelled.");
                return false;
            }
            
            String structureFileName = file.getName() + FileHelper.STRUCTURE_EXTENSION;
            callback.onLog("Upload complete.");
            callback.onLog("Structure file: " + structureFileName);
            callback.onComplete(structureFileName);
            return true;
            
        } catch (AuthorizationException e) {
            callback.onError("Authorization error: Please check your webhook URL");
            return false;
        } catch (UploadException e) {
            callback.onError(e.getMessage());
            return false;
        } catch (FileTooLargeException e) {
            callback.onError("File part is too large: " + e.getMessage());
            return false;
        } catch (IOException e) {
            callback.onError("IO error: " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            callback.onLog("Upload interrupted.");
            return false;
        } catch (Exception e) {
            callback.onError("Unexpected error: " + e.getMessage());
            return false;
        }
    }
    
    private void uploadNew(UploaderService uploader) throws IOException, InterruptedException, AuthorizationException, FileTooLargeException {
        long partsCount = FileHelper.calculateMaxPartCount(file.getAbsolutePath(), chunkSize);
        callback.onLog("Total parts to upload: " + partsCount);
        
        for (int i = 0; i < partsCount && !stopped; i++) {
            if (Thread.currentThread().isInterrupted()) {
                callback.onLog("Upload interrupted.");
                return;
            }
            
            int currentPart = i + 1;
            callback.onLog("Uploading part " + currentPart + "/" + partsCount);
            
            File partFile = FileHelper.getFilePart(file.getAbsolutePath(), i, chunkSize);
            boolean success = uploader.uploadSinglePart(partFile, file);
            
            if (success) {
                callback.onProgress(currentPart, (int) partsCount);
                callback.onLog("Part " + currentPart + "/" + partsCount + " uploaded successfully");
            } else {
                throw new IOException("Failed to upload part " + currentPart);
            }
        }
        
        if (!stopped) {
            callback.onLog("Generating thumbnail...");
            uploader.generateThumbnailForFile(file, thumbnailResolution, thumbnailQuality);
        }
    }
    
    private void uploadResume(UploaderService uploader, FileStruct existingStruct) throws IOException, InterruptedException, AuthorizationException, FileTooLargeException, FileMismatchException, UploadException {
        if (!existingStruct.isValid()) {
            callback.onError("Invalid structure file!");
        }
        
        long totalPartsCount = FileHelper.calculateMaxPartCount(file.getAbsolutePath(), existingStruct.getSinglePartSize());
        int badPartsCount = FileHelper.extractBadParts(existingStruct.getParts()).size();
        int uploadedPartsCount = existingStruct.getParts().size() - badPartsCount;
        
        callback.onLog("Already uploaded: " + uploadedPartsCount + "/" + totalPartsCount);
        
        boolean success = uploader.resumeUpload(file, existingStruct, (current, total) -> {
            callback.onProgress(current, total);
            callback.onLog("Part " + current + "/" + total + " uploaded successfully");
        });

        if (!success) {
            throw new IOException("Resume upload failed");
        }
        
        if (!stopped) {
            callback.onLog("Generating thumbnail...");
            uploader.generateThumbnailForFile(file, thumbnailResolution, thumbnailQuality);
        }
    }
    
    private FileStruct getExistingStructure() {
        try {
            FileStruct loadedStruct = FileHelper.loadStructureFile(new File(file.getName()));
            if (loadedStruct != null && loadedStruct.isValid()) {
                return loadedStruct;
            } else if (loadedStruct != null) {
                Logger.err(UploadTask.class, "Found existing structure file but failed to validate it.");
            }
        } catch (IOException e) {
            Logger.err(UploadTask.class, "Error loading existing structure: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Stop the upload task
     */
    public void stop() {
        stopped = true;
    }
}
