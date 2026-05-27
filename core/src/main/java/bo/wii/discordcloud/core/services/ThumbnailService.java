package bo.wii.discordcloud.core.services;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.thumbnail.api.ThumbnailGenerationException;
import bo.wii.discordcloud.thumbnail.api.ThumbnailGenerator;
import bo.wii.discordcloud.thumbnail.api.ThumbnailResolution;

import java.io.File;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class ThumbnailService {

    public static final ThumbnailResolution DEFAULT_RESOLUTION = ThumbnailResolution.HD_720P;
    public static final int DEFAULT_QUALITY = 75;
    private final List<ThumbnailGenerator> availableGenerators;

    /**
     * Loads available ThumbnailGenerator implementations from the "plugins" folder,
     * which should be located in the same directory as the main application JAR.
     * For this reason, the main application class must be passed as a parameter
     * in order to find the correct path relative to the jar file.
     *
     * @param mainAppClass main application class
     */
    public ThumbnailService(Class<?> mainAppClass) {
        this.availableGenerators = loadGeneratorsFromPluginsFolder(mainAppClass);
        System.out.println("Loaded " + availableGenerators.size() + " thumbnail providers.");
    }

    private List<ThumbnailGenerator> loadGeneratorsFromPluginsFolder(Class<?> mainAppClass) {
        try {
            // Find the path to the directory where the application is running
            File appDirectory = new File(mainAppClass.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
            File pluginsDirectory = new File(appDirectory, "plugins");

            System.out.println("Scanning 'plugins' directory: " + pluginsDirectory.getAbsolutePath());

            if (!pluginsDirectory.exists() || !pluginsDirectory.isDirectory()) {
                System.err.println("Can't load thumbnail providers: 'plugins' directory does not exist.");
                return new ArrayList<>();
            }

            // Find all .jar files in the 'plugins' folder
            File[] jarFiles = pluginsDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (jarFiles == null || jarFiles.length == 0) {
                System.out.println("No JAR files found in 'plugins' directory.");
                return new ArrayList<>();
            }

            List<URL> jarUrls = new ArrayList<>();
            for (File jarFile : jarFiles) {
                System.out.println("Found new provider: " + jarFile.getName());
                jarUrls.add(jarFile.toURI().toURL());
            }

            // Create a new ClassLoader that "sees" classes from these JARs
            URLClassLoader pluginClassLoader = new URLClassLoader(
                    jarUrls.toArray(new URL[0]),
                    Thread.currentThread().getContextClassLoader() // this allows plugins to see classes from the main application (ThumbnailGenerator interface)
            );

            // Use ServiceLoader with the new class loader to find implementations
            ServiceLoader<ThumbnailGenerator> serviceLoader = ServiceLoader.load(ThumbnailGenerator.class, pluginClassLoader);

            List<ThumbnailGenerator> generators = new ArrayList<>();
            Iterator<ThumbnailGenerator> iterator = serviceLoader.iterator();
            while (iterator.hasNext()) {
                try {
                    generators.add(iterator.next());
                } catch (ServiceConfigurationError e) {
                    System.err.println("Skipping thumbnail provider: " + e.getMessage());
                    if (e.getCause() != null) {
                        System.err.println("  Cause: " + e.getCause().getMessage());
                    }
                }
            }
            return generators;

        } catch (Exception e) {
            System.err.println("Error while loading plugins: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>(); // empty list on error
        }
    }

    public List<ThumbnailGenerator> getAvailableGenerators() {
        return Collections.unmodifiableList(availableGenerators);
    }

    public Optional<ThumbnailGenerator> findGeneratorFor(String fileExtension) {
        return availableGenerators.stream()
                .filter(generator -> generator.supports(fileExtension))
                .findFirst();
    }

    public void createThumbnail(File videoFile, OutputStream out) throws ThumbnailGenerationException {
        String extension = FileHelper.getFileExtension(videoFile);
        ThumbnailGenerator generator = findGeneratorFor(extension)
                .orElseThrow(() -> new ThumbnailGenerationException("File not supported", null));

        generator.generate(videoFile, out);
    }

    public void createThumbnail(File videoFile, OutputStream out, ThumbnailResolution resolution) throws ThumbnailGenerationException {
        String extension = FileHelper.getFileExtension(videoFile);
        ThumbnailGenerator generator = findGeneratorFor(extension)
                .orElseThrow(() -> new ThumbnailGenerationException("File not supported", null));

        generator.generate(videoFile, out, resolution);
    }

    public void createThumbnail(File videoFile, OutputStream out, ThumbnailResolution resolution, int quality) throws ThumbnailGenerationException {
        String extension = FileHelper.getFileExtension(videoFile);
        ThumbnailGenerator generator = findGeneratorFor(extension)
                .orElseThrow(() -> new ThumbnailGenerationException("File not supported", null));

        generator.generate(videoFile, out, resolution, quality);
    }

    /**
     * Generate thumbnail with specific resolution and return as byte array
     *
     * @param file The file to generate thumbnail for
     * @param resolution The desired resolution
     * @return byte array of thumbnail data or null if generation fails
     */
    public byte[] generateThumbnailBytes(File file, ThumbnailResolution resolution) {
        return generateThumbnailBytes(file, resolution, DEFAULT_QUALITY);
    }

    /**
     * Generate thumbnail with specific resolution and quality and return as byte array
     *
     * @param file The file to generate thumbnail for
     * @param resolution The desired resolution
     * @param quality The desired quality (50-100, where 100 is the best quality)
     * @return byte array of thumbnail data or null if generation fails
     */
    public byte[] generateThumbnailBytes(File file, ThumbnailResolution resolution, int quality) {
        try {
            File tempThumbnail = FileHelper.createTempThumbnailFile();

            try (OutputStream out = Files.newOutputStream(tempThumbnail.toPath(), StandardOpenOption.WRITE)) {
                // Generate thumbnail with specific resolution and quality
                // createThumbnail writes directly to the provided OutputStream
                createThumbnail(file, out, resolution, quality);

                // Read file to byte array
                return Files.readAllBytes(tempThumbnail.toPath());
            } finally {
                tempThumbnail.delete();
            }
        } catch (Exception e) {
            Logger.info(ThumbnailService.class, "Error when generating thumbnail: " + e.getMessage());
            return null;
        }
    }
}
