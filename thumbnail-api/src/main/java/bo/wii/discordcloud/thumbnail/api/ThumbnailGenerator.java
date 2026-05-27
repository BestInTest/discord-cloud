package bo.wii.discordcloud.thumbnail.api;

import java.io.File;
import java.io.OutputStream;

public interface ThumbnailGenerator {
    /**
     * Generates a thumbnail from the given video file and writes it to the output stream.
     * Uses the default resolution (1280x720).
     * @param videoFile The video file.
     * @param outputStream The stream to which the thumbnail will be written.
     * @throws ThumbnailGenerationException if an error occurs.
     */
    void generate(File videoFile, OutputStream outputStream) throws ThumbnailGenerationException;

    /**
     * Generates a thumbnail from the given video file and writes it to the output stream.
     * @param videoFile The video file.
     * @param outputStream The stream to which the thumbnail will be written.
     * @param resolution The target thumbnail resolution.
     * @throws ThumbnailGenerationException if an error occurs.
     */
    void generate(File videoFile, OutputStream outputStream, ThumbnailResolution resolution) throws ThumbnailGenerationException;

    /**
     * Generates a thumbnail from the given video file and writes it to the output stream.
     * @param videoFile The video file.
     * @param outputStream The stream to which the thumbnail will be written.
     * @param resolution The target thumbnail resolution.
     * @param quality The thumbnail quality (50-100, where 100 is the best quality).
     * @throws ThumbnailGenerationException if an error occurs.
     */
    default void generate(File videoFile, OutputStream outputStream, ThumbnailResolution resolution, int quality) throws ThumbnailGenerationException {
        generate(videoFile, outputStream, resolution);
    }

    /**
     * Checks whether this generator supports the given file format.
     * @param fileExtension The file extension (e.g. "mp4", "mkv")
     * @return true if supported, otherwise false.
     */
    boolean supports(String fileExtension);
}
