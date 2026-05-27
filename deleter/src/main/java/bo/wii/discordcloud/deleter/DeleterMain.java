package bo.wii.discordcloud.deleter;

import bo.wii.discordcloud.cli.AnsiColor;
import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.services.delete.DeleteTask;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.structure.enums.UploadType;
import bo.wii.discordcloud.core.utils.FileHelper;

import java.io.File;
import java.io.IOException;

public class DeleterMain {

    public static void main(String[] args) {
        // Parse arguments
        String inputFile = null;
        String webhookOverride = null;
        String botTokenOverride = null;
        boolean verbose = false;
        boolean quiet = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
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

        // Set log level
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
        final Configuration configuration = new Configuration("config-deleter.yml");

        // Determine effective credentials
        String webhook = webhookOverride != null ? webhookOverride : configuration.getWebhook();
        String botToken = botTokenOverride != null ? botTokenOverride : configuration.getBotToken();

        // Load structure file
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

        // Validate credentials based on upload type
        UploadType uploadType = structure.getUploadType() != null ? structure.getUploadType() : UploadType.WEBHOOK;

        if (uploadType == UploadType.BOT) {
            if (botToken == null || botToken.isEmpty()) {
                System.err.println("Error: Bot token is required for files uploaded via bot");
                System.err.println("Configure bot-token in config-deleter.yml or use --bot-token option");
                System.exit(3);
            }
        } else {
            if (webhook == null || webhook.isEmpty() || webhook.equals("your_webhook")) {
                System.err.println("Error: Webhook URL is required for files uploaded via webhook");
                System.err.println("Configure webhook in config-deleter.yml or use --webhook option");
                System.exit(3);
            }
        }

        // Print header
        System.out.println();
        System.out.println(AnsiColor.cyan("┌─────────────────────────────────────────────────┐"));
        System.out.println(AnsiColor.cyan("│") + AnsiColor.bold("             Discord Cloud Deleter               ") + AnsiColor.cyan("│"));
        System.out.println(AnsiColor.cyan("└─────────────────────────────────────────────────┘"));
        System.out.println();
        System.out.println(AnsiColor.yellow("  File:       ") + structure.getOriginalFileName());
        System.out.println(AnsiColor.yellow("  Size:       ") + AnsiColor.green(FileHelper.formatFileSize(structure.getFileSize())));
        System.out.println(AnsiColor.yellow("  Parts:      ") + AnsiColor.green(String.valueOf(structure.getParts().size())));
        System.out.println(AnsiColor.yellow("  Date:       ") + AnsiColor.green(FileHelper.formatTimestamp(structure.getUploadTimestamp())));
        System.out.println(AnsiColor.yellow("  Mode:       ") + AnsiColor.green(String.valueOf(uploadType)));
        System.out.println();

        // Create progress callback and run
        ConsoleDeleteProgressCallback progressCallback = new ConsoleDeleteProgressCallback(structure.getParts().size());
        progressCallback.start();

        DeleteTask deleteTask = new DeleteTask(structure, webhook, botToken, progressCallback);
        boolean success = deleteTask.execute();
        progressCallback.close();

        System.exit(success ? 0 : 6);
    }

    private static void printUsage() {
        String jarName = new File(DeleterMain.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath())
                .getName();

        System.out.println("Discord Cloud Deleter");
        System.out.println();
        System.out.println("Usage: java -jar " + jarName + " [OPTIONS] <file.dscl>");
        System.out.println();
        System.out.println("Deletes all Discord messages associated with the given structure file.");
        System.out.println("The upload mode (webhook or bot) is detected automatically from the structure file.");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -w, --webhook <url>        Override webhook URL from config");
        System.out.println("  -b, --bot-token <token>    Override bot token from config");
        System.out.println("  -v, --verbose              Show detailed debug output");
        System.out.println("  -q, --quiet                Show only errors");
        System.out.println("  -h, --help                 Show this help message");
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  For webhook-uploaded files:  requires the original webhook URL.");
        System.out.println("  For bot-uploaded files:      requires the bot token with MANAGE_MESSAGES permission.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar " + jarName + " file.dscl");
        System.out.println("  java -jar " + jarName + " --webhook https://discord.com/api/webhooks/... file.dscl");
        System.out.println("  java -jar " + jarName + " --bot-token YOUR_BOT_TOKEN file.dscl");
    }
}
