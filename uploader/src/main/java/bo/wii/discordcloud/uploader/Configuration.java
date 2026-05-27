package bo.wii.discordcloud.uploader;

import bo.wii.discordcloud.core.DiscordCloudCore;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class Configuration {

    private String webhook;
    private String botToken;
    private String channelId;
    private int chunkSize;

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
        channelId = config.getString("channel-id", ""); // kanał na który mają być wysyłane pliki przez bota
        chunkSize = config.getInt("chunk-size-mb", 10) * DiscordCloudCore.MB_SCALAR;
    }

    private void createNewConfig(File f) {
        YamlConfiguration config = new YamlConfiguration();

        config.set("webhook", "your_webhook");
        config.setComments("webhook", Arrays.asList(
                "Webhook URL for uploading files.",
                "You should use same webhook for later downloads.",
                "Leave empty if you use bot-token mode instead."));

        config.set("bot-token", "");
        config.setComments("bot-token", Arrays.asList(
                "",
                "Bot token for uploading files via Discord Bot API.",
                "If bot-token is set, it will be used instead of webhook.",
                "Leave empty to use webhook mode."));

        config.set("channel-id", "");
        config.setComments("channel-id", Arrays.asList(
                "",
                "Channel ID where files will be uploaded (required for bot mode).",
                "Bot should have access to this channel.",
                "To get channel ID: Enable Developer Mode in Discord, right-click channel -> Copy ID"));

        config.set("chunk-size-mb", 10);
        config.setComments("chunk-size-mb", Arrays.asList(
                "",
                "Size of each file chunk in MB (used in bot mode).",
                "Free/Non-Nitro: 10 MB",
                "Server Boost Level 2: 50 MB",
                "Server Boost Level 3: 100 MB"));

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

    public String getChannelId() {
        return channelId;
    }

    public int getChunkSize() {
        return chunkSize;
    }
}
