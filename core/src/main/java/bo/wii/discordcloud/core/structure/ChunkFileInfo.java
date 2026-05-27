package bo.wii.discordcloud.core.structure;

import bo.wii.discordcloud.core.structure.attachment.AttachmentAuth;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChunkFileInfo {
    private String name;
    private String sha256Hash; // hash of the split file part
    private String messageId;
    private String url;
    private boolean success; // whether the file was successfully uploaded

    public ChunkFileInfo(String name, String hash, String messageId, String url, boolean success) {
        this.name = name;
        this.sha256Hash = hash;
        this.messageId = messageId;
        this.url = url;
        this.success = success;
    }

    public String getName() {
        return name;
    }

    public int getPartNumber() {
        Pattern lastIntPattern = Pattern.compile("[^0-9]+([0-9]+)$");
        Matcher matcher = lastIntPattern.matcher(name);
        if (matcher.find()) {
            String numberStr = matcher.group(1);
            return Integer.parseInt(numberStr);
        }
        return -1;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getUrl() {
        return url;
    }

    public boolean isSuccess() {
        return success;
    }

    public AttachmentAuth getAttachmentAuth() {
        return new AttachmentAuth(url);
    }
}
