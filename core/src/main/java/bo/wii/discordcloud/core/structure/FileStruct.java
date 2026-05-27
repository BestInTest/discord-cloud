package bo.wii.discordcloud.core.structure;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.structure.enums.UploadType;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileStruct {

    public static final int CURRENT_VERSION = 2;
    public static final int MIN_SUPPORTED_VERSION = 1;

    // Pattern: /attachments/{channelId}/{attachmentId}/{filename}
    private static final Pattern CDN_ATTACHMENT_PATTERN = Pattern.compile("/attachments/(\\d+)/\\d+/");

    private final int fileVersion;
    private final long uploadTimestamp;
    private final String originalFileName; // file name
    private final long fileSize;
    private final String sha256Hash; // hash of the full file
    private final int singlePartSize;
    private UploadType uploadType; // Upload type (WEBHOOK, BOT)
    private String channelId; // Channel ID (required for refreshing via bot)
    private LinkedHashSet<ChunkFileInfo> parts;
    private String thumbnailBase64;

    public FileStruct(String originalFileName, String hash, LinkedHashSet<ChunkFileInfo> parts, UploadType uploadType, String channelId, int chunkSize) {
        this.fileVersion = CURRENT_VERSION;
        this.uploadTimestamp = System.currentTimeMillis();
        File f = new File(originalFileName);
        this.originalFileName = f.getName();
        this.fileSize = f.length();
        this.sha256Hash = hash;
        this.singlePartSize = chunkSize;
        this.parts = parts;
        this.uploadType = uploadType != null ? uploadType : UploadType.WEBHOOK;
        this.channelId = channelId;
    }

    public int getFileVersion() {
        return fileVersion;
    }

    public long getUploadTimestamp() {
        return uploadTimestamp;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public int getSinglePartSize() {
        return singlePartSize;
    }

    public UploadType getUploadType() {
        return uploadType;
    }

    public void setUploadType(UploadType uploadType) {
        this.uploadType = uploadType;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public LinkedHashSet<ChunkFileInfo> getParts() {
        return parts;
    }

    public void setParts(LinkedHashSet<ChunkFileInfo> parts) {
        this.parts = parts;
    }

    public String getThumbnailBase64() {
        return thumbnailBase64;
    }

    public void setThumbnailBase64(String thumbnailBase64) {
        this.thumbnailBase64 = thumbnailBase64;
    }

    /**
     * Returns the channel ID - first from the channelId field, and if empty,
     * attempts to extract it from the URL of the first part.
     * Useful when the structure comes from a webhook (no channelId field),
     * and we want to use the bot to refresh links.
     * @return the channel ID, or null if it could not be determined
     */
    public String getEffectiveChannelId() {
        if (channelId != null && !channelId.isEmpty()) {
            return channelId;
        }
        // Fallback: extract ID from the URL of the first chunk
        if (parts != null && !parts.isEmpty()) {
            String url = parts.iterator().next().getUrl();
            Logger.log(FileStruct.class, "Channel ID is missing in structure, attempting to extract from attachment URL");
            return extractChannelIdFromUrl(url);
        }
        return null;
    }

    /**
     * Extracts the channel ID from a Discord CDN URL.
     * @param url attachment URL from Discord CDN
     * @return the channel ID, or null if it could not be extracted
     */
    private String extractChannelIdFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        Matcher matcher = CDN_ATTACHMENT_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Validates the structure values.
     * Can be used when it is uncertain whether the loaded JSON
     * matches the structure of this class.
     * @return whether the file was loaded correctly
     */
    public boolean isValid() {
        return (fileVersion > 0) && (fileSize > 0) && (singlePartSize > 0) && (originalFileName != null) && (sha256Hash != null) && (parts != null) && (!parts.isEmpty());
    }

    /**
     * Checks if at least one link has expired
     */
    public boolean isExpired() {
        for (ChunkFileInfo part : parts) {
            if (part.getAttachmentAuth().isExpired()) {
                return true;
            }
        }
        return false;
    }
}
