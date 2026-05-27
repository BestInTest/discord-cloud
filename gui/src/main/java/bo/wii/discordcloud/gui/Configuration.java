package bo.wii.discordcloud.gui;

import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class Configuration {

    private final File configFile;

    // Global settings
    private boolean clearTemp;
    private boolean prefetchEnabled;
    private boolean checkPartHash;

    // Active profile
    private String selectedWebhook;

    // Profiles
    private LinkedHashMap<String, String> webhooks;
    private LinkedHashMap<String, BotProfile> bots;

    /**
     * Bot config profile with token and channel ID
     */
    public static class BotProfile {
        private String token;
        private String channelId;

        public BotProfile(String token, String channelId) {
            this.token = token != null ? token : "";
            this.channelId = channelId != null ? channelId : "";
        }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getChannelId() { return channelId; }
        public void setChannelId(String channelId) { this.channelId = channelId; }
    }

    Configuration(String file) {
        configFile = new File(file);
        webhooks = new LinkedHashMap<>();
        bots = new LinkedHashMap<>();
        if (!configFile.exists()) {
            createNewConfig(configFile);
        }
        loadConfig(configFile);
    }

    private void loadConfig(File f) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(f);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        // Read global settings
        this.clearTemp = config.getBoolean("clear-temp", true);
        this.prefetchEnabled = config.getBoolean("prefetch", true);
        this.checkPartHash = config.getBoolean("check-part-hash", false);
        this.selectedWebhook = config.getString("selected-webhook", "default");

        // Load profiles
        this.webhooks = loadWebhookMap(config);
        this.bots = loadBotMap(config);

        // Ensure selected webhook exists
        if (!webhooks.containsKey(selectedWebhook) && !webhooks.isEmpty()) {
            selectedWebhook = webhooks.keySet().iterator().next();
            saveConfiguration();
        }
    }

    private void createNewConfig(File f) {
        YamlConfiguration config = new YamlConfiguration();

        config.set("clear-temp", true);
        config.set("prefetch", true);
        config.set("check-part-hash", false);
        config.set("selected-webhook", "default");
        config.createSection("webhooks", getDefaultWebhookMap());
        config.createSection("bots");

        setComments(config);

        //Save config to file
        try {
            config.save(f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void saveConfiguration() {
        YamlConfiguration config = new YamlConfiguration();

        config.set("clear-temp", clearTemp);
        config.set("prefetch", prefetchEnabled);
        config.set("check-part-hash", checkPartHash);
        config.set("selected-webhook", selectedWebhook);

        // Save webhooks
        if (webhooks.isEmpty()) {
            config.createSection("webhooks");
        } else {
            config.createSection("webhooks", new LinkedHashMap<>(webhooks));
        }

        // Save bot profiles
        ConfigurationSection botsSection = config.createSection("bots");
        for (Map.Entry<String, BotProfile> entry : bots.entrySet()) {
            ConfigurationSection botSection = botsSection.createSection(entry.getKey());
            botSection.set("token", entry.getValue().getToken());
            botSection.set("channel-id", entry.getValue().getChannelId());
        }

        setComments(config);
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setComments(YamlConfiguration config) {
        config.setComments("prefetch", Arrays.asList("", "Enable prefetch for faster downloads.",
                "Default: true"));
        config.setComments("check-part-hash", Arrays.asList("", "Enable SHA256 hash verification for each downloaded part.",
                "This ensures data integrity but may slow down the download slightly.",
                "Full file hash is still verified after all parts are downloaded.",
                "Default: false"));
        config.setComments("selected-webhook", Arrays.asList("", "The name of the currently selected webhook.",
                "Must be one configured in the 'webhooks' section."));
        config.setComments("webhooks", Arrays.asList("", "Webhook config (name: url).",
                "You can add multiple webhooks for different channels.",
                "You should use same webhook as when uploading files."));
        config.setComments("bots", Arrays.asList("", "Bot config.",
                "Each bot profile has a token and channel-id.",
                "Used for bot-mode uploads and downloads."));
    }

    private LinkedHashMap<String, String> getDefaultWebhookMap() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("default", "your_webhook");
        return map;
    }

    private LinkedHashMap<String, String> loadWebhookMap(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("webhooks");
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                map.put(key, section.getString(key));
            }
        }
        return map;
    }

    private LinkedHashMap<String, BotProfile> loadBotMap(YamlConfiguration config) {
        LinkedHashMap<String, BotProfile> map = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("bots");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection botSection = section.getConfigurationSection(key);
                if (botSection != null) {
                    String token = botSection.getString("token", "");
                    String channelId = botSection.getString("channel-id", "");
                    map.put(key, new BotProfile(token, channelId));
                }
            }
        }
        return map;
    }

    /**
     * Retrieves the webhook URL associated with the given name.
     *
     * @param name the name of the webhook to retrieve
     * @return the webhook URL corresponding to the provided name, or null if no such webhook exists
     */
    public String getWebhookUrl(String name) {
        return webhooks.get(name);
    }

    /**
     * Retrieves all configured webhook URLs.
     *
     * @return a LinkedHashMap containing the names and corresponding URLs of all webhooks.
     */
    public LinkedHashMap<String, String> getWebhooks() {
        return webhooks;
    }

    public void addWebhook(String name, String url) {
        webhooks.put(name, url);
        saveConfiguration();
    }

    public void removeWebhook(String name) {
        webhooks.remove(name);
        if (name.equals(selectedWebhook) && !webhooks.isEmpty()) {
            selectedWebhook = webhooks.keySet().iterator().next();
        }
        saveConfiguration();
    }

    public void updateWebhook(String name, String newUrl) {
        if (webhooks.containsKey(name)) {
            webhooks.put(name, newUrl);
            saveConfiguration();
        }
    }

    public LinkedHashMap<String, BotProfile> getBots() {
        return bots;
    }

    public BotProfile getBot(String name) {
        return bots.get(name);
    }

    public void addBot(String name, String token, String channelId) {
        bots.put(name, new BotProfile(token, channelId));
        saveConfiguration();
    }

    public void removeBot(String name) {
        bots.remove(name);
        saveConfiguration();
    }

    public void updateBot(String name, String token, String channelId) {
        BotProfile profile = bots.get(name);
        if (profile != null) {
            profile.setToken(token);
            profile.setChannelId(channelId);
            saveConfiguration();
        }
    }


    public boolean isClearTemp() {
        return clearTemp;
    }

    /**
     * Sets whether temporary files should be deleted after they have been uploaded or downloaded.
     * This setting is automatically saved to the configuration file.
     *
     * @param clearTemp whether temporary files should be deleted after uploading or downloading
     */
    public void setClearTemp(boolean clearTemp) {
        this.clearTemp = clearTemp;
        saveConfiguration();
    }

    /**
     * Retrieves the name of the currently selected webhook.
     *
     * @return the name of the selected webhook.
     */
    public String getSelectedWebhook() {
        return selectedWebhook;
    }

    /**
     * Sets the name of the currently selected webhook.
     * This setting is automatically saved to the configuration file.
     *
     * @param selectedWebhook the name of the webhook to be set as selected
     */
    public void setSelectedWebhook(String selectedWebhook) {
        this.selectedWebhook = selectedWebhook;
        saveConfiguration();
    }

    /**
     * Checks if prefetch is enabled.
     *
     * @return true if prefetch is enabled, false otherwise
     */
    public boolean isPrefetchEnabled() {
        return prefetchEnabled;
    }

    /**
     * Sets the prefetch option.
     * This setting is automatically saved to the configuration file.
     *
     * @param prefetchEnabled true to enable prefetch, false to disable
     */
    public void setPrefetchEnabled(boolean prefetchEnabled) {
        this.prefetchEnabled = prefetchEnabled;
        saveConfiguration();
    }

    /**
     * Checks if SHA256 hash verification for each downloaded part is enabled.
     *
     * @return true if part hash checking is enabled, false otherwise
     */
    public boolean isCheckPartHash() {
        return checkPartHash;
    }

    /**
     * Sets whether SHA256 hash verification for each downloaded part is enabled.
     * This setting is automatically saved to the configuration file.
     *
     * @param checkPartHash true to enable part hash checking, false to disable
     */
    public void setCheckPartHash(boolean checkPartHash) {
        this.checkPartHash = checkPartHash;
        saveConfiguration();
    }
}
