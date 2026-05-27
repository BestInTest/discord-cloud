package bo.wii.discordcloud.deleter;

import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class Configuration {

    private String webhook;
    private String botToken;

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

        webhook = config.getString("webhook", "your_webhook");
        botToken = config.getString("bot-token", "");
    }

    private void createNewConfig(File f) {
        YamlConfiguration config = new YamlConfiguration();

        config.set("webhook", "your_webhook");
        config.setComments("webhook", Arrays.asList(
                "Webhook URL used to delete webhook-uploaded files.",
                "Must be the same webhook that was used when uploading.",
                "Leave as 'your_webhook' or empty if you only use bot-token mode."));

        config.set("bot-token", "");
        config.setComments("bot-token", Arrays.asList(
                "",
                "Bot token used to delete bot-uploaded files.",
                "Required for files that were uploaded via bot token mode.",
                "Leave empty if you only use webhook uploads."));

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
}

