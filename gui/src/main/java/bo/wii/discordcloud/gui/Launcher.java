package bo.wii.discordcloud.gui;

import bo.wii.discordcloud.core.services.ThumbnailService;
import bo.wii.discordcloud.core.utils.FileHelper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class Launcher {
    private static Configuration configuration;
    private static String pendingFile = null;

    public static void main(String[] args) {
        // FOR DEBUGGING
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("-thumbnailtest")) {
                thumbnailTest();
                return;
            }
            if (args[0].equalsIgnoreCase("-generationtest")) {
                generationTest(args);
                return;
            }
        }

        // Check if a .dscl or .json file was passed as the first argument (file association)
        if (args.length > 0 && !args[0].startsWith("-")) {
            File f = new File(args[0]);
            if (f.exists() && (f.getName().endsWith(".dscl") || f.getName().endsWith(".json"))) {
                pendingFile = args[0];
            }
        }

        configuration = new Configuration("config-gui.yml");

        DiscordCloudGui.main(args);

    }

    public static Configuration getConfiguration() {
        return configuration;
    }

    public static String getPendingFile() {
        return pendingFile;
    }


    //
    //    \/ FOR DEBUGGING PURPOSES ONLY \/
    //

    public static void thumbnailTest() {
        ThumbnailService service = new ThumbnailService(Launcher.class);
        System.out.println("Available Thumbnail Generators:");
        service.getAvailableGenerators().forEach(ext ->
                System.out.println(" - " + ext)
        );
        String testExt = "jpg";
        service.findGeneratorFor(testExt).ifPresentOrElse(
                generator -> System.out.println("Found generator for " + testExt + ": " + generator.getClass().getName()),
                () -> System.out.println("No generator found for " + testExt)
        );
    }

    public static void generationTest(String... args) {
        if (args.length < 2) {
            System.out.println("Please provide file path as second argument.");
            return;
        }
        ThumbnailService service = new ThumbnailService(Launcher.class);
        File img = new File(args[1]);
        String testExt = FileHelper.getFileExtension(img);
        service.findGeneratorFor(testExt).ifPresent(generator -> {
                    try {
                        File output = new File("thumbnail_output.jpg");
                        output.createNewFile();
                        generator.generate(img, Files.newOutputStream(output.toPath(), StandardOpenOption.WRITE));
                        System.out.println("Thumbnail generated: " + output.getAbsolutePath());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        );
    }
}
