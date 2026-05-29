package bo.wii.discordcloud.server.cli;

public class ParsedArgs {

    public final String webhookOverride;
    public final Boolean prefetchOverride;
    public final Boolean sslOverride;
    public final String hostOverride;
    public final Integer portOverride;
    public final boolean generateToken;
    public final boolean removeToken;
    public final String tokenToRemove;

    public ParsedArgs(
            String webhookOverride,
            Boolean prefetchOverride,
            Boolean sslOverride,
            String hostOverride,
            Integer portOverride,
            boolean generateToken,
            boolean removeToken,
            String tokenToRemove
    ) {
        this.webhookOverride = webhookOverride;
        this.prefetchOverride = prefetchOverride;
        this.sslOverride = sslOverride;
        this.hostOverride = hostOverride;
        this.portOverride = portOverride;
        this.generateToken = generateToken;
        this.removeToken = removeToken;
        this.tokenToRemove = tokenToRemove;
    }
}

