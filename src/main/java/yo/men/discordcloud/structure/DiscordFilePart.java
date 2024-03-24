package yo.men.discordcloud.structure;

import yo.men.discordcloud.structure.attachment.AttachmentAuth;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordFilePart {
    private String name;
    private String sha256Hash; // hash podzielonego pliku
    private String messageId;
    private String url;
    private boolean success; // czy plik został poprawnie przesłany

    public DiscordFilePart(String name, String hash, String messageId, String url, boolean success) {
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
