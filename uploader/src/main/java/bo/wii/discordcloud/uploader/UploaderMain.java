package bo.wii.discordcloud.uploader;

import bo.wii.discordcloud.cli.AnsiColor;
import bo.wii.discordcloud.core.DiscordCloudCore;
import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.services.upload.UploadBotTask;
import bo.wii.discordcloud.core.services.upload.UploadTask;
import bo.wii.discordcloud.core.utils.FileHelper;

import java.io.File;

public class UploaderMain {

    private enum UploadMode { WEBHOOK, BOT }

    public static void main(String[] args) {
        // Parse arguments
        String inputFile = null;
        String webhookOverride = null;
        String tokenOverride = null;
        String channelOverride = null;
        Integer chunkSizeOverride = null;
        UploadMode modeOverride = null;
        boolean verbose = false;
        boolean quiet = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--mode", "-m" -> {
                    if (i + 1 < args.length) {
                        String modeStr = args[++i].toLowerCase();
                        switch (modeStr) {
                            case "webhook" -> modeOverride = UploadMode.WEBHOOK;
                            case "bot" -> modeOverride = UploadMode.BOT;
                            default -> {
                                System.err.println("Error: Invalid mode. Must be 'webhook' or 'bot'");
                                printUsage();
                                System.exit(1);
                            }
                        }
                    } else {
                        System.err.println("Error: --mode requires a value");
                        printUsage();
                        System.exit(1);
                    }
                }
                case "--webhook", "-w" -> {
                    if (i + 1 < args.length) {
                        webhookOverride = args[++i];
                    } else {
                        System.err.println("Error: --webhook requires a value");
                        printUsage();
                        System.exit(1);
                    }
                }
                case "--token", "-t" -> {
                    if (i + 1 < args.length) {
                        tokenOverride = args[++i];
                    } else {
                        System.err.println("Error: --token requires a value");
                        printUsage();
                        System.exit(1);
                    }
                }
                case "--channel", "-c" -> {
                    if (i + 1 < args.length) {
                        channelOverride = args[++i];
                    } else {
                        System.err.println("Error: --channel requires a value");
                        printUsage();
                        System.exit(1);
                    }
                }
                case "--chunk-size", "-s" -> {
                    if (i + 1 < args.length) {
                        try {
                            chunkSizeOverride = Integer.parseInt(args[++i]) * DiscordCloudCore.MB_SCALAR;
                        } catch (NumberFormatException e) {
                            System.err.println("Error: --chunk-size must be a number");
                            printUsage();
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Error: --chunk-size requires a value");
                        printUsage();
                        System.exit(1);
                    }
                }
                case "--verbose", "-v" -> verbose = true;
                case "--quiet", "-q" -> quiet = true;
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                }
                default -> {
                    if (!arg.startsWith("-")) {
                        if (inputFile == null) {
                            inputFile = arg;
                        } else {
                            System.err.println("Error: Multiple input files specified");
                            printUsage();
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Error: Unknown option: " + arg);
                        printUsage();
                        System.exit(1);
                    }
                }
            }
        }

        if (inputFile == null) {
            System.err.println("Error: No input file specified");
            printUsage();
            System.exit(1);
        }

        // Set log level based on flags
        if (verbose) {
            Logger.setLevel(Logger.LogLevel.DEBUG);
        } else if (quiet) {
            Logger.setLevel(Logger.LogLevel.ERROR);
        } else {
            Logger.setLevel(Logger.LogLevel.INFO);
        }

        File file = new File(inputFile);
        if (!file.exists()) {
            System.err.println("Error: File does not exist: " + file.getAbsolutePath());
            System.exit(2);
        }

        // Load configuration
        final Configuration configuration = new Configuration("config-uploader.yml");

        // Determine upload mode (default: webhook)
        UploadMode mode = modeOverride != null ? modeOverride : UploadMode.WEBHOOK;

        boolean success;

        if (mode == UploadMode.WEBHOOK) {
            // Webhook mode (default)
            int chunkSize = chunkSizeOverride != null ? chunkSizeOverride : configuration.getChunkSize();
            String webhook = webhookOverride != null ? webhookOverride : configuration.getWebhook();

            if (webhook == null || webhook.isEmpty() || webhook.equals("your_webhook")) {
                System.err.println("Error: Webhook not configured");
                System.err.println("Configure webhook in config-uploader.yml or use --webhook option");
                System.exit(3);
            }

            long totalParts = FileHelper.calculateMaxPartCount(file.getAbsolutePath(), chunkSize);

            printHeader(file, chunkSize, (int) totalParts, "WEBHOOK");

            ConsoleUploadProgressCallback progressCallback = new ConsoleUploadProgressCallback(
                    file.length(), (int) totalParts, chunkSize);
            progressCallback.start();

            UploadTask uploadTask = new UploadTask(file, webhook, chunkSize, progressCallback);
            success = uploadTask.execute();
            progressCallback.close();

        } else {

            // Bot token mode
            int chunkSize = chunkSizeOverride != null ? chunkSizeOverride : configuration.getChunkSize();
            String token = tokenOverride != null ? tokenOverride : configuration.getBotToken();
            String channelId = channelOverride != null ? channelOverride : configuration.getChannelId();

            if (token == null || token.isEmpty()) {
                System.err.println("Error: Bot token not configured");
                System.err.println("Configure bot-token in config-uploader.yml or use --token option");
                System.exit(3);
            }

            if (channelId == null || channelId.isEmpty()) {
                System.err.println("Error: Channel ID not configured");
                System.err.println("Configure channel-id in config-uploader.yml or use --channel option");
                System.exit(3);
            }

            long totalParts = FileHelper.calculateMaxPartCount(file.getAbsolutePath(), chunkSize);

            printHeader(file, chunkSize, (int) totalParts, "BOT");

            ConsoleUploadProgressCallback progressCallback = new ConsoleUploadProgressCallback(
                    file.length(), (int) totalParts, chunkSize);
            progressCallback.start();

            UploadBotTask uploadTask = new UploadBotTask(file, token, channelId, chunkSize, progressCallback);
            success = uploadTask.execute();
            progressCallback.close();
        }

        System.exit(success ? 0 : 4);
    }

    private static void printHeader(File file, int chunkSize, int totalParts, String mode) {
        System.out.println();
        System.out.println(AnsiColor.cyan("┌─────────────────────────────────────────────────┐"));
        System.out.println(AnsiColor.cyan("│") + AnsiColor.bold("             Discord Cloud Uploader              ") + AnsiColor.cyan("│"));
        System.out.println(AnsiColor.cyan("└─────────────────────────────────────────────────┘"));
        System.out.println();
        System.out.println(AnsiColor.yellow("  File:       ") + file.getName());
        System.out.println(AnsiColor.yellow("  Size:       ") + AnsiColor.green(FileHelper.formatFileSize(file.length())));
        System.out.println(AnsiColor.yellow("  Chunk size: ") + AnsiColor.green(FileHelper.formatFileSize(chunkSize)));
        System.out.println(AnsiColor.yellow("  Parts:      ") + AnsiColor.green(String.valueOf(totalParts)));
        System.out.println(AnsiColor.yellow("  Mode:       ") + AnsiColor.green(mode));
        System.out.println();
    }

    private static void printUsage() {
        String jarName = new File(UploaderMain.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath())
                .getName();

        System.out.println("Discord Cloud Uploader");
        System.out.println();
        System.out.println("Usage: java -jar " + jarName + " [OPTIONS] <file>");
        System.out.println();
        System.out.println("Upload modes:");
        System.out.println("  Webhook (default) - uses Discord webhook to upload files");
        System.out.println("  Bot               - uses Discord bot token to upload files (supports larger chunks)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -m, --mode <webhook|bot>   Select upload mode (default: webhook)");
        System.out.println("  -w, --webhook <url>        Override webhook URL from config");
        System.out.println("  -t, --token <token>        Override bot token from config");
        System.out.println("  -c, --channel <id>         Override channel ID from config (only for bot uploads)");
        System.out.println("  -s, --chunk-size <mb>      Override chunk size in MB");
        System.out.println("  -v, --verbose              Show detailed debug output");
        System.out.println("  -q, --quiet                Show only errors");
        System.out.println("  -h, --help                 Show this help message");
        System.out.println();
        System.out.println("Chunk sizes based on Discord limits:");
        System.out.println("  Free/Non-Nitro: 10 MB");
        System.out.println("  Server Boost Level 2: 50 MB");
        System.out.println("  Server Boost Level 3: 100 MB");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar " + jarName + " file.zip");
        System.out.println("  java -jar " + jarName + " --webhook https://discord.com/api/webhooks/... file.zip");
        System.out.println("  java -jar " + jarName + " --mode bot file.zip");
        System.out.println("  java -jar " + jarName + " --mode bot --token BOT_TOKEN --channel CHANNEL_ID file.zip");
        System.out.println("  java -jar " + jarName + " --mode bot --chunk-size 100 file.zip");
    }
}