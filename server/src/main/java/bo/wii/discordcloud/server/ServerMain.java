package bo.wii.discordcloud.server;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.server.api.RouterSetup;
import bo.wii.discordcloud.server.auth.LoginRateLimiter;
import bo.wii.discordcloud.server.auth.SessionManager;
import bo.wii.discordcloud.server.auth.TokenManager;
import bo.wii.discordcloud.server.cli.ArgsParser;
import bo.wii.discordcloud.server.cli.CliPrinter;
import bo.wii.discordcloud.server.cli.ParsedArgs;
import bo.wii.discordcloud.server.config.ServerConfig;
import io.javalin.Javalin;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.IOException;

public class ServerMain {

    private static final String CONFIG_FILE = "config-server.yml";

    public static void main(String[] args) {
        ParsedArgs parsed = ArgsParser.parse(args);

        // Token management commands exit immediately without starting the server
        if (parsed.generateToken || parsed.removeToken) {
            handleTokenCommands(parsed);
        }

        ServerConfig config = new ServerConfig(CONFIG_FILE);

        // Console arguments override config file values
        if (parsed.hostOverride != null) {
            config.setHost(parsed.hostOverride);
        }
        if (parsed.portOverride != null) {
            config.setPort(parsed.portOverride);
        }
        if (parsed.sslOverride != null) {
            config.setSsl(parsed.sslOverride);
        }
        if (parsed.prefetchOverride != null) {
            config.setPrefetchEnabled(parsed.prefetchOverride);
        }
        if (parsed.webhookOverride != null) {
            config.setWebhook(parsed.webhookOverride);
        }

        String host = config.getHost();
        int port = config.getPort();
        boolean ssl = config.isSsl();
        boolean prefetch = config.isPrefetchEnabled();

        // Validate SSL keystore settings before starting
        if (ssl) {
            if (config.getKeystoreFilePath().isBlank() || "path/to/keystore.p12".equals(config.getKeystoreFilePath())) {
                Logger.error(ServerMain.class, "SSL enabled but keystoreFilePath is not configured.");
                System.exit(1);
            }
            if (config.getKeystorePassword().isBlank() || "your_keystore_password".equals(config.getKeystorePassword())) {
                Logger.error(ServerMain.class, "SSL enabled but keystorePassword is not configured.");
                System.exit(1);
            }
        }

        // Auth setup
        TokenManager tokenManager = config.isRequireToken()
                ? new TokenManager(config.getTokenFile())
                : null;
        SessionManager sessionManager = new SessionManager();
        LoginRateLimiter rateLimiter = new LoginRateLimiter(
                config.getLoginRateLimitMaxFailures(),
                config.getLoginRateLimitWindowSeconds());

        CliPrinter.printBanner(host, port, ssl, prefetch,
                config.isRequireToken(),
                tokenManager != null ? tokenManager.getTokenCount() : 0);

        final boolean finalSsl = ssl;
        final String finalHost = host;
        final int finalPort = port;

        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.showJavalinBanner = false;

            if (finalSsl) {
                javalinConfig.jetty.addConnector((server, httpConfig) ->
                        buildSslConnector(server, httpConfig, config, finalHost, finalPort));
            }
        });

        RouterSetup router = new RouterSetup(config, tokenManager, sessionManager, rateLimiter);
        router.registerRoutes(app);

        app.start(host, port);
    }

    private static ServerConnector buildSslConnector(Server server, HttpConfiguration baseHttpConfig, ServerConfig config, String host, int port) {
        SslContextFactory.Server ssl = new SslContextFactory.Server();
        ssl.setKeyStorePath(config.getKeystoreFilePath());
        ssl.setKeyStorePassword(config.getKeystorePassword());
        if (!config.getKeystoreAlias().isBlank()) {
            ssl.setCertAlias(config.getKeystoreAlias());
        }

        HttpConfiguration httpsConfig = new HttpConfiguration(baseHttpConfig);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        ServerConnector connector = new ServerConnector(server,
                new SslConnectionFactory(ssl, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(httpsConfig));
        connector.setHost(host);
        connector.setPort(port);
        return connector;
    }

    /**
     * Handles --generate-token and --remove-token commands and exits.
     */
    private static void handleTokenCommands(ParsedArgs parsed) {
        ServerConfig config = new ServerConfig(CONFIG_FILE);
        TokenManager tokenManager = new TokenManager(config.getTokenFile());

        if (parsed.generateToken) {
            try {
                String token = tokenManager.generateAndStoreToken();
                System.out.println("New token generated (save it - it will not be shown again):");
                System.out.println(token);
                System.out.println("Total tokens: " + tokenManager.getTokenCount());
                System.exit(0);
            } catch (IOException e) {
                System.err.println("Error generating token: " + e.getMessage());
                System.exit(1);
            }
        }

        if (parsed.removeToken) {
            try {
                boolean removed = tokenManager.removeToken(parsed.tokenToRemove);
                if (removed) {
                    System.out.println("Token removed successfully.");
                    System.out.println("Remaining tokens: " + tokenManager.getTokenCount());
                } else {
                    System.err.println("Error: Token not found.");
                    System.exit(1);
                }
                System.exit(0);
            } catch (IOException e) {
                System.err.println("Error removing token: " + e.getMessage());
                System.exit(1);
            }
        }
    }
}

