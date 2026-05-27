package bo.wii.discordcloud.downloader;

import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class Configuration {

    private String webhook;
    private String botToken;
    private boolean prefetchEnabled;
    private boolean checkPartHash;

    Configuration(String configFile) {
        File file = new File(configFile);
        if (!file.exists()) {
            createNewConfig(file);
        }
        loadConfig(file);
    }

    private void loadConfig(File f) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(f);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        // Read values
        webhook = config.getString("webhook", "your_webhook");
        botToken = config.getString("bot-token", "");
        prefetchEnabled = config.getBoolean("prefetch", true);
        checkPartHash = config.getBoolean("check-part-hash", false);
    }

    private void createNewConfig(File f) {
        YamlConfiguration config = new YamlConfiguration();

        config.set("webhook", "your_webhook");
        config.setComments("webhook", Arrays.asList("Webhook URL for downloading files.", "You should use same webhook as when uploading files.", "Used for files uploaded via webhook."));

        config.set("bot-token", "");
        config.setComments("bot-token", Arrays.asList("", "Bot token for downloading files.", "Required for files uploaded using bot token mode but can also be used for files uploaded via webhook.", "Leave empty if you only use webhook uploads."));

        config.set("prefetch", true);
        config.setComments("prefetch", Arrays.asList("", "Enable prefetch for faster downloads.", "Prefetch will refresh links for upcoming parts in advance.", "Default: true"));

        config.set("check-part-hash", false);
        config.setComments("check-part-hash", Arrays.asList("", "Enable SHA256 hash verification for each downloaded part.", "This ensures data integrity but may slow down the download slightly.", "Default: false"));

        //Save config to file
        try {
            config.save(f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getWebhook() {
        return webhook;
    }

    public String getBotToken() {
        return botToken;
    }

    public boolean isPrefetchEnabled() {
        return prefetchEnabled;
    }

    public boolean isCheckPartHash() {
        return checkPartHash;
    }

}
