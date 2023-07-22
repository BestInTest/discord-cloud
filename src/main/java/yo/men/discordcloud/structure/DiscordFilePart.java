package yo.men.discordcloud.structure;

public class DiscordFilePart {
    private String name;
    private String sha256Hash; // hash podzielonego pliku
    //TODO: maxPartSize - może nie? Sam fileSize powinien wystarczyć
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
}
