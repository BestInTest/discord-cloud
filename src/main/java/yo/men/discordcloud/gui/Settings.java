package yo.men.discordcloud.gui;

public class Settings {
    private boolean clearCache;
    private String webhookUrl;
    //todo: dodać chunksize i może fileVersion

    public boolean isClearCache() {
        return clearCache;
    }

    public void setClearCache(boolean clearCache) {
        this.clearCache = clearCache;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }
}
