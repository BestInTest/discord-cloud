package bo.wii.discordcloud.server.cli;

public class ArgsParser {

    public static ParsedArgs parse(String[] args) {
        String webhookOverride = null;
        Boolean prefetchOverride = null;
        Boolean sslOverride = null;
        String hostOverride = null;
        Integer portOverride = null;
        boolean generateToken = false;
        boolean removeToken = false;
        String tokenToRemove = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--webhook", "-w" -> {
                    if (i + 1 < args.length) {
                        webhookOverride = args[++i];
                    } else {
                        System.err.println("Error: --webhook requires a value");
                        CliPrinter.printUsage();
                        System.exit(1);
                    }
                }
                case "--no-prefetch", "-np" -> prefetchOverride = false;
                case "--prefetch", "-p"     -> prefetchOverride = true;
                case "--no-ssl"             -> sslOverride = false;
                case "--ssl"                -> sslOverride = true;
                case "--host", "-h" -> {
                    if (i + 1 < args.length) {
                        hostOverride = args[++i];
                    } else {
                        System.err.println("Error: --host requires a value");
                        CliPrinter.printUsage();
                        System.exit(1);
                    }
                }
                case "--port" -> {
                    if (i + 1 < args.length) {
                        try {
                            portOverride = Integer.parseInt(args[++i]);
                            if (portOverride < 1 || portOverride > 65535) {
                                System.err.println("Error: Port must be between 1 and 65535");
                                System.exit(1);
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Invalid port number");
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Error: --port requires a value");
                        CliPrinter.printUsage();
                        System.exit(1);
                    }
                }
                case "--generate-token", "-gt" -> generateToken = true;
                case "--remove-token", "-rt" -> {
                    if (i + 1 < args.length) {
                        removeToken = true;
                        tokenToRemove = args[++i];
                    } else {
                        System.err.println("Error: --remove-token requires a token value");
                        CliPrinter.printUsage();
                        System.exit(1);
                    }
                }
                case "--help", "-?" -> {
                    CliPrinter.printUsage();
                    System.exit(0);
                }
                default -> {
                    System.err.println("Error: Unknown option: " + arg);
                    CliPrinter.printUsage();
                    System.exit(1);
                }
            }
        }

        return new ParsedArgs(
                webhookOverride, prefetchOverride, sslOverride,
                hostOverride, portOverride, generateToken, removeToken, tokenToRemove
        );
    }
}

