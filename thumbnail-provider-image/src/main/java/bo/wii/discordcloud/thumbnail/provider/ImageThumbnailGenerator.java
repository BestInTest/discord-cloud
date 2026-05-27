package bo.wii.discordcloud.thumbnail.provider;

import bo.wii.discordcloud.thumbnail.api.ThumbnailGenerator;
import bo.wii.discordcloud.thumbnail.api.ThumbnailGenerationException;
import bo.wii.discordcloud.thumbnail.api.ThumbnailResolution;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

public class ImageThumbnailGenerator implements ThumbnailGenerator {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "bmp", "gif", "webp", "tiff", "tif"
    );

    private static final ThumbnailResolution DEFAULT_RESOLUTION = ThumbnailResolution.HD_720P;
    private static final int DEFAULT_QUALITY = 75;

    @Override
    public void generate(File imageFile, OutputStream outputStream) throws ThumbnailGenerationException {
        generate(imageFile, outputStream, DEFAULT_RESOLUTION, DEFAULT_QUALITY);
    }

    @Override
    public void generate(File imageFile, OutputStream outputStream, ThumbnailResolution resolution) throws ThumbnailGenerationException {
        generate(imageFile, outputStream, resolution, DEFAULT_QUALITY);
    }

    @Override
    public void generate(File imageFile, OutputStream outputStream, ThumbnailResolution resolution, int quality) throws ThumbnailGenerationException {
        System.out.println("Generating thumbnail: " + imageFile.getName()
                + " (" + resolution + ", " + quality + "%)");

        // Convert quality from percentage (50-100) to a value for ImageIO (0.0-1.0)
        // 100% -> 1.0 (best quality)
        float jpegQuality = quality / 100.0f;
        jpegQuality = Math.max(0.5f, Math.min(1.0f, jpegQuality)); // Limit to the 0.5-1.0 range

        try {
            // Load the image
            BufferedImage originalImage = ImageIO.read(imageFile);
            if (originalImage == null) {
                throw new ThumbnailGenerationException("Cannot load image: " + imageFile.getName(), null);
            }

            // Calculate thumbnail dimensions while preserving aspect ratio
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            double aspectRatio = (double) originalWidth / originalHeight;
            int targetWidth = resolution.getWidth();
            int targetHeight = resolution.getHeight();

            if (aspectRatio > ((double) resolution.getWidth() / resolution.getHeight())) {
                // The image is wider - adjust height
                targetHeight = (int) (resolution.getWidth() / aspectRatio);
            } else {
                // The image is taller - adjust width
                targetWidth = (int) (resolution.getHeight() * aspectRatio);
            }

            // Create the thumbnail
            BufferedImage thumbnail = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = thumbnail.createGraphics();

            // Set scaling quality
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw the scaled image
            graphics.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
            graphics.dispose();

            // ImageWriter with settings for jpg
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam writeParam = writer.getDefaultWriteParam();

            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(jpegQuality);
            }

            // Write the thumbnail to the stream
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(thumbnail, null, null), writeParam);
                writer.dispose();
            }

        } catch (IOException e) {
            throw new ThumbnailGenerationException("I/O error while generating image thumbnail", e);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return SUPPORTED_EXTENSIONS.contains(fileExtension.toLowerCase());
    }
}
