package bo.wii.discordcloud.server.cli;

public class CliPrinter {

    public static void printBanner(String host, int port, boolean ssl, boolean prefetch, boolean requireToken, int tokenCount) {
        String protocol = ssl ? "https" : "http";
        String displayHost = "0.0.0.0".equals(host) ? "localhost" : host;

        System.out.println("--------------------------------------");
        System.out.println("Host:     " + host);
        System.out.println("Port:     " + port);
        System.out.println("SSL:      " + (ssl ? "enabled" : "disabled"));
        System.out.println("Prefetch: " + (prefetch ? "enabled" : "disabled"));
        System.out.println("Auth:     " + (requireToken ? "enabled (" + tokenCount + " token(s))" : "disabled"));
        System.out.println("--------------------------------------");
        System.out.println("Access at: " + protocol + "://" + displayHost + ":" + port);
        System.out.println("--------------------------------------");
    }

    public static void printUsage() {
        System.out.println("Usage: java -jar discord-cloud-server.jar [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -w,  --webhook <url>         Override webhook URL from config");
        System.out.println("  -p,  --prefetch              Force enable prefetch");
        System.out.println("  -np, --no-prefetch           Force disable prefetch");
        System.out.println("       --ssl                   Enable SSL");
        System.out.println("       --no-ssl                Disable SSL");
        System.out.println("  -h,  --host <host>           Set server host (default: 0.0.0.0)");
        System.out.println("       --port <port>           Set server port (default: 26025)");
        System.out.println("  -gt, --generate-token        Generate new access token and exit");
        System.out.println("  -rt, --remove-token <token>  Remove access token and exit");
        System.out.println("       --help                  Show this help message");
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  - Tokens are stored as SHA-256 hashes. The raw value is shown only once.");
        System.out.println("  - CLI flags take priority over config file values.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar discord-cloud-server.jar");
        System.out.println("  java -jar discord-cloud-server.jar --webhook https://discord.com/...");
        System.out.println("  java -jar discord-cloud-server.jar --no-prefetch --port 8080");
        System.out.println("  java -jar discord-cloud-server.jar --generate-token");
        System.out.println("  java -jar discord-cloud-server.jar --remove-token <token>");
    }
}
