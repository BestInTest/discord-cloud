package bo.wii.discordcloud.downloader;

import bo.wii.discordcloud.cli.AnsiColor;
import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.client.BotTokenDiagnostics;
import bo.wii.discordcloud.core.client.LinkRefresher;
import bo.wii.discordcloud.core.client.LinkRefresherFactory;
import bo.wii.discordcloud.core.services.download.DownloadTask;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.structure.enums.UploadType;
import bo.wii.discordcloud.core.utils.FileHelper;

import java.io.File;
import java.io.IOException;

public class DownloaderMain {

    private enum DownloadMode { WEBHOOK, BOT, AUTO }

    public static void main(String[] args) {
        // Parse arguments
        String inputFile = null;
        String webhookOverride = null;
        String botTokenOverride = null;
        Boolean prefetchOverride = null;
        Boolean checkPartHashOverride = null;
        boolean diagnoseMode = false;
        boolean verbose = false;
        boolean quiet = false;
        DownloadMode modeOverride = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--mode", "-m" -> {
                    if (i + 1 < args.length) {
                        String modeStr = args[++i].toLowerCase();
                        switch (modeStr) {
                            case "webhook" -> modeOverride = DownloadMode.WEBHOOK;
                            case "bot" -> modeOverride = DownloadMode.BOT;
                            case "auto" -> modeOverride = DownloadMode.AUTO;
                            default -> {
                                System.err.println("Error: Invalid mode. Must be 'webhook', 'bot' or 'auto'");
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
                case "--bot-token", "-b" -> {
                    if (i + 1 < args.length) {
                        botTokenOverride = args[++i];
                    } else {
                        System.err.println("Error: --bot-token requires a value");
                        printUsage();
                        System.exit(1);
                    }
                }
                case "--diagnose", "-d" -> diagnoseMode = true;
                case "--verbose", "-v" -> verbose = true;
                case "--quiet", "-q" -> quiet = true;
                case "--no-prefetch", "-np" -> prefetchOverride = false;
                case "--prefetch", "-p" -> prefetchOverride = true;
                case "--check-part-hash", "-cph" -> checkPartHashOverride = true;
                case "--no-check-part-hash", "-ncph" -> checkPartHashOverride = false;
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

        final Configuration configuration = new Configuration("config-downloader.yml");

        // Determine webhook and bot token to use
        String webhook = webhookOverride != null ? webhookOverride : configuration.getWebhook();
        String botToken = botTokenOverride != null ? botTokenOverride : configuration.getBotToken();

        // Determine prefetch setting
        boolean prefetchEnabled = prefetchOverride != null ? prefetchOverride : configuration.isPrefetchEnabled();

        // Determine hash checking setting
        boolean checkPartHash = checkPartHashOverride != null ? checkPartHashOverride : configuration.isCheckPartHash();

        FileStruct structure = null;
        try {
            structure = FileHelper.loadStructureFile(file);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Cannot load structure file: " + file.getAbsolutePath());
            System.exit(4);
        }

        if (structure == null) {
            System.err.println("Structure file is invalid: " + file.getAbsolutePath());
            System.exit(5);
        }

        UploadType effectiveType = resolveUploadType(modeOverride, structure);

        // Run diagnostics if requested
        if (diagnoseMode) {
            if (effectiveType == UploadType.BOT) {

                if (botToken == null || botToken.isEmpty()) {
                    System.err.println("Error: Bot token is required for diagnostics");
                    System.err.println("Set bot-token in config-downloader.yml or use --bot-token option");
                    System.exit(7);
                }

                String channelId = structure.getEffectiveChannelId();
                if (channelId == null || channelId.isEmpty()) {
                    System.err.println("Error: Channel ID is missing in structure file and could not be extracted from attachment URL");
                    System.exit(8);
                }

                // Get first message ID for testing
                String messageId = structure.getParts().iterator().next().getMessageId();

                BotTokenDiagnostics diagnostics = new BotTokenDiagnostics(botToken);
                boolean success = diagnostics.runFullDiagnostic(channelId, messageId);

                System.exit(success ? 0 : 9);
            } else {
                System.out.println("File was uploaded via WEBHOOK");
                System.out.println("Diagnostics are only available for files uploaded via BOT");
                System.exit(0);
            }
        }

        // Determine which LinkRefresher to use based on upload type
        LinkRefresher linkRefresher;
        try {
            linkRefresher = createRefresher(effectiveType, structure, webhook, botToken);

            System.out.println();
            System.out.println(AnsiColor.cyan("┌─────────────────────────────────────────────────┐"));
            System.out.println(AnsiColor.cyan("│") + AnsiColor.bold("            Discord Cloud Downloader             ") + AnsiColor.cyan("│"));
            System.out.println(AnsiColor.cyan("└─────────────────────────────────────────────────┘"));
            System.out.println();
            System.out.println(AnsiColor.yellow("  File:       ") + structure.getOriginalFileName());
            System.out.println(AnsiColor.yellow("  Size:       ") + AnsiColor.green(FileHelper.formatFileSize(structure.getFileSize())));
            System.out.println(AnsiColor.yellow("  Parts:      ") + AnsiColor.green(String.valueOf(structure.getParts().size())));
            System.out.println(AnsiColor.yellow("  Date:       ") + AnsiColor.green(FileHelper.formatTimestamp(structure.getUploadTimestamp())));
            System.out.println(AnsiColor.yellow("  Mode:       ") + AnsiColor.green(String.valueOf(structure.getUploadType() != null ? structure.getUploadType() : "WEBHOOK")));
            System.out.println(AnsiColor.yellow("  Refresh:    ") + AnsiColor.green(String.valueOf(linkRefresher.getType()))
                    + (modeOverride != null && modeOverride != DownloadMode.AUTO ? AnsiColor.dim(" (forced)") : ""));
            System.out.println(AnsiColor.yellow("  Prefetch:   ") + (prefetchEnabled ? AnsiColor.green("enabled") : AnsiColor.red("disabled")));
            if (checkPartHash) {
                System.out.println(AnsiColor.yellow("  Hash check: ") + AnsiColor.green("enabled"));
            }
            System.out.println();

        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.println("Please configure the required credentials in config-downloader.yml");
            System.err.println("or provide them via command line options.");
            System.exit(3);
            return;
        }

        // Create progress callback
        ConsoleDownloadProgressCallback progressCallback = new ConsoleDownloadProgressCallback(
                structure.getFileSize(),
                structure.getParts().size()
        );
        progressCallback.start();

        // Create download task with console callback
        DownloadTask downloadTask = new DownloadTask(structure, linkRefresher, progressCallback, prefetchEnabled, checkPartHash);

        // Execute download
        boolean success = downloadTask.execute();
        progressCallback.close();
        System.exit(success ? 0 : 6);
    }

    /**
     * Resolve the effective upload type based on mode override and structure file.
     */
    private static UploadType resolveUploadType(DownloadMode modeOverride, FileStruct structure) {
        if (modeOverride != null && modeOverride != DownloadMode.AUTO) {
            return switch (modeOverride) {
                case WEBHOOK -> UploadType.WEBHOOK;
                case BOT -> UploadType.BOT;
                default -> UploadType.WEBHOOK;
            };
        }

        // Auto detect from structure
        UploadType uploadType = structure.getUploadType();
        return uploadType != null ? uploadType : UploadType.WEBHOOK;
    }

    private static LinkRefresher createRefresher(UploadType type, FileStruct structure, String webhook, String botToken) {
        return switch (type) {
            case BOT -> {
                if (botToken == null || botToken.isEmpty()) {
                    throw new IllegalArgumentException("Bot token is required for bot mode. Set bot-token in config or use --bot-token option.");
                }
                String channelId = structure.getEffectiveChannelId();
                if (channelId == null || channelId.isEmpty()) {
                    throw new IllegalArgumentException("Channel ID is missing in structure file and could not be extracted from attachment URL");
                }
                yield LinkRefresherFactory.createBotTokenRefresher(botToken, channelId);
            }
            case WEBHOOK -> {
                if (webhook == null || webhook.isEmpty() || webhook.equals("your_webhook")) {
                    throw new IllegalArgumentException("Webhook URL is required for webhook mode. Set webhook in config or use --webhook option.");
                }
                yield LinkRefresherFactory.createWebhookRefresher(webhook);
            }
        };
    }

    private static void printUsage() {
        String jarName = new File(DownloaderMain.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath())
                .getName();

        System.out.println("Discord Cloud Downloader");
        System.out.println();
        System.out.println("Usage: java -jar " + jarName + " [OPTIONS] <file.dscl>");
        System.out.println();
        System.out.println("Download modes:");
        System.out.println("  Auto (default)  - automatically detect from structure file");
        System.out.println("  Webhook         - force webhook refresh method");
        System.out.println("  Bot             - force bot token refresh method");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -m, --mode <auto|webhook|bot>  Select download mode (default: auto)");
        System.out.println("  -w, --webhook <url>            Override webhook URL from config");
        System.out.println("  -b, --bot-token <token>        Override bot token from config");
        System.out.println("  -d, --diagnose                 Test bot token and permissions (only for debugging)");
        System.out.println("  -v, --verbose                  Show detailed debug output");
        System.out.println("  -q, --quiet                    Show only errors");
        System.out.println("  -p, --prefetch                 Force enable prefetch");
        System.out.println("  -np, --no-prefetch             Force disable prefetch");
        System.out.println("  -cph, --check-part-hash        Force enable SHA256 hash verification for parts");
        System.out.println("  -ncph, --no-check-part-hash    Force disable SHA256 hash verification for parts");
        System.out.println("  -h, --help                     Show this help message");
        System.out.println();
        System.out.println("Note:");
        System.out.println("  By default the program detects the upload method from the structure file.");
        System.out.println("  Use --mode to override this (e.g. to download webhook-uploaded files via bot token).");
        System.out.println();
        System.out.println("Diagnostics:");
        System.out.println("  Use --diagnose to test if your bot token is valid and has required permissions.");
        System.out.println("  This will test: bot token validity, channel access, and message read permissions.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar " + jarName + " file.dscl");
        System.out.println("  java -jar " + jarName + " --mode webhook file.dscl");
        System.out.println("  java -jar " + jarName + " --mode bot --bot-token YOUR_BOT_TOKEN file.dscl");
        System.out.println("  java -jar " + jarName + " --webhook https://discord.com/api/webhooks/... file.dscl");
        System.out.println("  java -jar " + jarName + " --diagnose --bot-token YOUR_BOT_TOKEN file.dscl");
        System.out.println("  java -jar " + jarName + " --no-prefetch file.dscl");
        System.out.println("  java -jar " + jarName + " --check-part-hash file.dscl");
    }
}