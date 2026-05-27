package bo.wii.discordcloud.core.services.upload;

import bo.wii.discordcloud.core.DiscordCloudCore;
import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.exceptions.AuthorizationException;
import bo.wii.discordcloud.core.exceptions.FileMismatchException;
import bo.wii.discordcloud.core.exceptions.FileTooLargeException;
import bo.wii.discordcloud.core.exceptions.UploadException;
import bo.wii.discordcloud.core.services.ThumbnailService;
import bo.wii.discordcloud.core.structure.ChunkFileInfo;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.structure.enums.UploadType;
import bo.wii.discordcloud.core.utils.Base64Util;
import bo.wii.discordcloud.core.utils.FileHashCalculator;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.thumbnail.api.ThumbnailResolution;

import java.io.File;
import java.io.IOException;
import java.util.Stack;
import java.util.function.BiConsumer;

/**
 * Base class for uploader services with common functionality
 */
public abstract class BaseUploaderService {

    public final int MAX_PART_SIZE;

    protected BaseUploaderService(int chunkFileSize) {
        MAX_PART_SIZE = chunkFileSize;
    }

    /**
     * Upload single part of a file
     * @param partFile The part file to upload
     * @param originalFile The original file
     * @return true if successful, false otherwise
     */
    public abstract boolean uploadSinglePart(File partFile, File originalFile)
            throws IOException, AuthorizationException, FileTooLargeException, InterruptedException;

    /**
     * Get the upload type this service uses
     * @return The upload type
     */
    protected abstract UploadType getUploadType();

    /**
     * Get the channel ID this service uploads to (null for webhook)
     * @return The channel ID or null
     */
    protected abstract String getChannelId();

    /**
     * Resume upload with progress callback
     * @param originalFile The original file
     * @param existingStructure The existing structure
     * @param progressCallback Callback for progress updates (current, total)
     * @return true if successful
     */
    public boolean resumeUpload(File originalFile, FileStruct existingStructure, BiConsumer<Integer, Integer> progressCallback)
            throws IOException, InterruptedException, AuthorizationException, FileTooLargeException, FileMismatchException, UploadException {

        if (!existingStructure.isValid()) {
            throw new IOException("Invalid structure file: " + originalFile.getName());
        }

        // Validate file
        validateFile(originalFile, existingStructure);
        
        // Validate upload method compatibility
        validateUploadMethod(existingStructure);

        long totalPartsCount = FileHelper.calculateMaxPartCount(originalFile.getAbsolutePath(), existingStructure.getSinglePartSize());
        int badPartsCount = FileHelper.extractBadParts(existingStructure.getParts()).size();
        int uploadedPartsCount = existingStructure.getParts().size() - badPartsCount;

        // Check if file is fully uploaded
        if (uploadedPartsCount == totalPartsCount) {
            Logger.log(getClass(), "File is already fully uploaded: " + originalFile.getName());
            return true;
        }

        // Check if file is corrupted
        if (uploadedPartsCount > totalPartsCount) {
            Logger.err(getClass(), "File is corrupted: " + originalFile.getName());
            Logger.err(getClass(), "Total parts: " + totalPartsCount + " Uploaded parts: " + uploadedPartsCount);
            return false;
        }

        Logger.log(getClass(), "Resuming upload: " + originalFile.getName());

        // Check if some parts failed to upload
        Stack<ChunkFileInfo> badUploads = new Stack<>();
        for (ChunkFileInfo part : existingStructure.getParts()) {
            if (!part.isSuccess()) {
                badUploads.add(part);
            }
        }

        long partsToUpload = totalPartsCount;

        for (int partNum = uploadedPartsCount; partNum < partsToUpload; partNum++) {
            File partFile = getPartFile(originalFile, partNum, badUploads, existingStructure.getSinglePartSize());

            boolean success = uploadSinglePart(partFile, originalFile);
            if (!success) {
                Logger.err(getClass(), "Failed to upload part " + (partNum + 1));
                return false;
            }

            if (progressCallback != null) {
                progressCallback.accept(partNum + 1, (int) partsToUpload);
            }
        }

        return true;
    }

    /**
     * Generate thumbnail with default resolution and save to structure file
     * @param originalFile The file to generate thumbnail for
     */
    public void generateThumbnailForFile(File originalFile) {
        generateThumbnailForFile(originalFile, ThumbnailService.DEFAULT_RESOLUTION, ThumbnailService.DEFAULT_QUALITY);
    }

    /**
     * Generate thumbnail with specific resolution and save to structure file
     * @param originalFile The file to generate thumbnail for
     * @param resolution The desired resolution
     */
    public void generateThumbnailForFile(File originalFile, ThumbnailResolution resolution) {
        generateThumbnailForFile(originalFile, resolution, ThumbnailService.DEFAULT_QUALITY);
    }

    /**
     * Generate thumbnail with specific resolution and quality and save to structure file
     * @param originalFile The file to generate thumbnail for
     * @param resolution The desired resolution
     * @param quality The desired quality (50-100, where 100 is the best quality)
     */
    public void generateThumbnailForFile(File originalFile, ThumbnailResolution resolution, int quality) {
        try {
            byte[] thumbnailBytes = DiscordCloudCore.thumbnailService.generateThumbnailBytes(originalFile, resolution, quality);
            if (thumbnailBytes != null) {
                String imgInBase64 = Base64Util.bytesToBase64(thumbnailBytes);

                // new File() from original file name because structure files are always in the same location as program jar
                // and originalFile can be from anywhere, so we cannot rely on originalFile path to load structure file
                FileStruct structure = FileHelper.loadStructureFile(new File(originalFile.getName()));
                if (structure != null) {
                    FileHelper.saveThumbnailToStructure(structure, imgInBase64);
                    Logger.log(getClass(), "Thumbnail generated and saved for file: " + originalFile.getName() + " (quality: " + quality + "%)");
                } else {
                    Logger.err(getClass(), "Cannot save thumbnail: structure is null for file " + originalFile.getName());
                }
            } else {
                Logger.log(getClass(), "No suitable thumbnail generator found or error occurred for file: " + originalFile.getName());
            }
        } catch (Exception e) {
            Logger.err(getClass(), "Error when generating thumbnail: " + e.getMessage());
        }
    }

    /**
     * Validate file against structure
     * @param originalFile The file to validate
     * @param existingStructure The structure to validate against
     * @throws FileMismatchException If validation fails
     */
    protected void validateFile(File originalFile, FileStruct existingStructure) throws FileMismatchException {
        if (!originalFile.getName().equals(existingStructure.getOriginalFileName())) {
            throw new FileMismatchException("File name mismatch: " + originalFile.getName() + " != " + existingStructure.getOriginalFileName());
        }
        String hash = FileHashCalculator.getFileHash(originalFile);
        if (!hash.equals(existingStructure.getSha256Hash())) {
            throw new FileMismatchException("File hash mismatch: " + hash + " != " + existingStructure.getSha256Hash());
        }
    }

    /**
     * Validate upload method compatibility
     * @param existingStructure The existing structure
     */
    protected void validateUploadMethod(FileStruct existingStructure) throws UploadException {
        UploadType currentUploadType = getUploadType();
        UploadType existingUploadType = existingStructure.getUploadType();
        
        // If existing structure doesn't have uploadType, assume WEBHOOK (backward compatibility)
        if (existingUploadType == null) {
            existingUploadType = UploadType.WEBHOOK;
        }
        
        // Check if upload types match
        if (currentUploadType != existingUploadType) {
            throw new UploadException(
                "Upload type mismatch: Cannot continue upload with different upload method.\n" +
                "  Existing: " + existingUploadType + "\n" +
                "  Current:  " + currentUploadType + "\n" +
                "Please use the same upload method."
            );
        }
        
        // Check if chunk sizes match
        int existingChunkSize = existingStructure.getSinglePartSize();
        if (MAX_PART_SIZE != existingChunkSize) {
            throw new UploadException(
                "Chunk size mismatch: Cannot continue upload with different chunk size.\n" +
                "  Existing: " + existingChunkSize + " (" + FileHelper.formatFileSize(existingChunkSize) + ")\n" +
                "  Current:  " + MAX_PART_SIZE + " (" + FileHelper.formatFileSize(MAX_PART_SIZE) + ")\n" +
                "Please use the same chunk size."
            );
        }

        // For BOT uploads, also check channel ID
        if (currentUploadType == UploadType.BOT) {
            String currentChannelId = getChannelId();
            String existingChannelId = existingStructure.getChannelId();
            
            if (currentChannelId == null || existingChannelId == null) {
                throw new UploadException(
                    "Channel ID is missing. Cannot validate upload compatibility."
                );
            }
            
            if (!currentChannelId.equals(existingChannelId)) {
                throw new UploadException(
                    "Channel ID mismatch: Cannot continue upload to different channel.\n" +
                    "  Existing: " + existingChannelId + "\n" +
                    "  Current:  " + currentChannelId + "\n" +
                    "Please use the same channel."
                );
            }
        }
    }

    /**
     * Get part file, either new or from failed uploads
     * @param originalFile The original file
     * @param partNum The part number
     * @param badUploads Stack of failed uploads
     * @param singlePartSize The size of a single part
     * @return The part file
     * @throws IOException If unable to create part file
     */
    protected File getPartFile(File originalFile, int partNum, Stack<ChunkFileInfo> badUploads, int singlePartSize) throws IOException {
        if (badUploads.isEmpty()) {
            return FileHelper.getFilePart(originalFile.getAbsolutePath(), partNum, singlePartSize);
        } else {
            ChunkFileInfo badPart = badUploads.pop();
            return FileHelper.getFilePart(originalFile.getAbsolutePath(), badPart.getPartNumber(), singlePartSize);
        }
    }
}
