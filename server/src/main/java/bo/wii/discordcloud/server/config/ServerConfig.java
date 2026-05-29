package bo.wii.discordcloud.server.config;

import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class ServerConfig {

    private String host;
    private int port;
    private boolean ssl;
    private String keystoreFilePath;
    private String keystorePassword;
    private String keystoreAlias;
    private String webhook;
    private String botToken;
    private boolean autoRemoveDownloadedFiles;
    private boolean prefetchEnabled;
    private boolean requireToken;
    private int sessionDurationSeconds;
    private String tokenFile;
    private String filesDirectory;
    private String cacheDirectory;
    private int loginRateLimitMaxFailures;
    private int loginRateLimitWindowSeconds;

    public ServerConfig(String configFile) {
        File file = new File(configFile);
        if (!file.exists()) {
            createDefaultConfig(file);
        }
        loadConfig(file);
        createRequiredDirectories();
    }

    private void loadConfig(File f) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(f);
        } catch (IOException | InvalidConfigurationException e) {
            System.err.println("[ServerConfig] Failed to load config: " + e.getMessage());
        }

        host = config.getString("host", "0.0.0.0");
        port = config.getInt("port", 26025);
        ssl = config.getBoolean("ssl", false);
        keystoreFilePath = config.getString("keystoreFilePath", "path/to/keystore.p12");
        keystorePassword = config.getString("keystorePassword", "your_keystore_password");
        keystoreAlias = config.getString("alias", "cert");
        webhook = config.getString("webhook", "");
        botToken = config.getString("botToken", "");
        autoRemoveDownloadedFiles = config.getBoolean("autoRemoveDownloadedFiles", true);
        prefetchEnabled = config.getBoolean("prefetch", true);
        requireToken = config.getBoolean("requireToken", false);
        sessionDurationSeconds = config.getInt("sessionDurationSeconds", 43200);
        tokenFile = config.getString("tokenFile", "access-tokens.json");
        filesDirectory = config.getString("filesDirectory", "files");
        cacheDirectory = config.getString("cacheDirectory", ".server-cache");
        loginRateLimitMaxFailures = config.getInt("loginRateLimitMaxFailures", 10);
        loginRateLimitWindowSeconds = config.getInt("loginRateLimitWindowSeconds", 600);
    }

    private void createDefaultConfig(File f) {
        YamlConfiguration config = new YamlConfiguration();

        config.set("host", "0.0.0.0");
        config.setComments("host", Arrays.asList("Bind address. Use 127.0.0.1 to expose the server only locally.", "Default: 0.0.0.0"));

        config.set("port", 26025);
        config.setComments("port", Arrays.asList("", "Port for the HTTP server.", "Default: 26025"));

        config.set("ssl", false);
        config.setComments("ssl", Arrays.asList("", "Enable HTTPS.", "Default: false"));
        config.set("keystoreFilePath", "path/to/keystore.p12");
        config.setComments("keystoreFilePath", Arrays.asList("Path to a PKCS12 (.p12) or JKS keystore used when ssl=true."));
        config.set("keystorePassword", "your_keystore_password");
        config.set("alias", "cert");
        config.setComments("alias", Arrays.asList("Preferred certificate alias when the keystore contains multiple entries."));

        config.set("webhook", "");
        config.setComments("webhook", Arrays.asList("", "Webhook URL used to refresh links for files uploaded in WEBHOOK mode.", "Can be left empty if you only serve BOT-mode files."));

        config.set("botToken", "");
        config.setComments("botToken", Arrays.asList("", "Discord bot token used to refresh links for files uploaded in BOT mode.", "Can be left empty if you only serve WEBHOOK-mode files."));

        config.set("autoRemoveDownloadedFiles", true);
        config.setComments("autoRemoveDownloadedFiles", Arrays.asList("", "Delete temporary downloaded chunks after sending them to the client.", "Default: true"));

        config.set("prefetch", true);
        config.setComments("prefetch", Arrays.asList("", "Enable part prefetching for faster sequential streaming.", "Default: true"));

        config.set("requireToken", false);
        config.setComments("requireToken", Arrays.asList("", "Require a valid access token or authenticated browser session for API and file routes.", "Start server with '--help' option to see more details", "Default: false"));

        config.set("sessionDurationSeconds", 43200);
        config.setComments("sessionDurationSeconds", Arrays.asList("", "Lifetime of browser sessions created by /api/auth.", "Default: 43200 (12 hours)"));

        config.set("tokenFile", "access-tokens.json");
        config.setComments("tokenFile", Arrays.asList("", "JSON file used for storing hashed access tokens."));

        config.set("filesDirectory", "files");
        config.setComments("filesDirectory", Arrays.asList("", "Directory containing .dscl structure files."));

        config.set("cacheDirectory", ".server-cache");
        config.setComments("cacheDirectory", Arrays.asList("", "Directory for storing temporarily downloaded file chunks."));

        config.set("loginRateLimitMaxFailures", 10);
        config.setComments("loginRateLimitMaxFailures", Arrays.asList("", "Maximum number of failed login attempts before blocking an IP."));

        config.set("loginRateLimitWindowSeconds", 600);
        config.setComments("loginRateLimitWindowSeconds", Arrays.asList("Time window (in seconds) for counting failures."));

        try {
            config.save(f);
        } catch (IOException e) {
            System.err.println("[ServerConfig] Failed to save default config: " + e.getMessage());
        }
    }

    private void createRequiredDirectories() {
        File filesDir = new File(filesDirectory);
        if (!filesDir.exists()) {
            filesDir.mkdirs();
        }
        File cacheDir = new File(cacheDirectory);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public void setWebhook(String webhook) {
        this.webhook = webhook;
    }

    public void setPrefetchEnabled(boolean prefetchEnabled) {
        this.prefetchEnabled = prefetchEnabled;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isSsl() {
        return ssl;
    }

    public String getKeystoreFilePath() {
        return keystoreFilePath;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public String getKeystoreAlias() {
        return keystoreAlias;
    }

    public String getWebhook() {
        return webhook;
    }

    public String getBotToken() {
        return botToken;
    }

    public boolean isAutoRemoveDownloadedFiles() {
        return autoRemoveDownloadedFiles;
    }

    public boolean isPrefetchEnabled() {
        return prefetchEnabled;
    }

    public boolean isRequireToken() {
        return requireToken;
    }

    public int getSessionDurationSeconds() {
        return sessionDurationSeconds;
    }

    public String getTokenFile() {
        return tokenFile;
    }

    public String getFilesDirectory() {
        return filesDirectory;
    }

    public String getCacheDirectory() {
        return cacheDirectory;
    }

    public int getLoginRateLimitMaxFailures() {
        return loginRateLimitMaxFailures;
    }

    public int getLoginRateLimitWindowSeconds() {
        return loginRateLimitWindowSeconds;
    }
}
