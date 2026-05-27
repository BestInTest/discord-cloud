package bo.wii.discordcloud.thumbnail.provider;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import bo.wii.discordcloud.thumbnail.api.ThumbnailGenerator;
import bo.wii.discordcloud.thumbnail.api.ThumbnailGenerationException;
import bo.wii.discordcloud.thumbnail.api.ThumbnailResolution;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class FfmpegThumbnailGenerator implements ThumbnailGenerator {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "mp4", "mkv", "mov", "avi", "webm", "flv", "wmv", "mpeg", "mpg", "3gp", "ts", "m4v"
    );

    private static final ThumbnailResolution DEFAULT_RESOLUTION = ThumbnailResolution.HD_720P;
    private static final int DEFAULT_QUALITY = 75;

    private final FFmpegExecutor executor;
    private final FFprobe ffprobe;

    public FfmpegThumbnailGenerator() throws ThumbnailGenerationException {
        try {
            String ffmpegPath  = PlatformHelper.resolveBundledExecutable("ffmpeg");
            String ffprobePath = PlatformHelper.resolveBundledExecutable("ffprobe");

            FFmpeg ffmpeg = new FFmpeg(ffmpegPath);
            this.ffprobe  = new FFprobe(ffprobePath);
            this.executor = new FFmpegExecutor(ffmpeg, ffprobe);
        } catch (IOException e) {
            throw new ThumbnailGenerationException(
                    "FFMpeg or FFprobe was not found. Make sure the ffmpeg/<platform>/ directory " +
                    "exists next to the plugins/ folder, or that ffmpeg is installed and available in PATH.", e);
        }
    }

    @Override
    public void generate(File videoFile, OutputStream outputStream) throws ThumbnailGenerationException {
        generate(videoFile, outputStream, DEFAULT_RESOLUTION, DEFAULT_QUALITY);
    }

    @Override
    public void generate(File videoFile, OutputStream outputStream, ThumbnailResolution resolution) throws ThumbnailGenerationException {
        generate(videoFile, outputStream, resolution, DEFAULT_QUALITY);
    }

    @Override
    public void generate(File videoFile, OutputStream outputStream, ThumbnailResolution resolution, int quality) throws ThumbnailGenerationException {
        System.out.println("Generating thumbnail: " + videoFile.getName()
                + " (" + resolution + ", " + quality + "%)");

        Path tempFile = null;
        try {
            // Get video information to determine its duration
            FFmpegProbeResult probeResult = ffprobe.probe(videoFile.getAbsolutePath());
            double durationInSeconds = probeResult.getFormat().duration;

            // Calculate the midpoint of the video (or 1 second if the video is very short)
            double midpointInSeconds = Math.max(1.0, durationInSeconds / 2.0);

            System.out.println("Generating thumbnail at position: " + midpointInSeconds + "s");

            // Convert quality from percentage (50-100) to FFmpeg q:v value (2-31, where 2 is the best quality)
            // 100% -> q:v 2 (best)
            // 50% -> q:v 31 (worst)
            int qValue = (int) (31 - ((quality - 50) / 50.0 * 29));
            qValue = Math.max(2, Math.min(31, qValue)); // Limit to the 2-31 range

            // Create a temporary file for the thumbnail
            String tempName = "thumb-" + ThreadLocalRandom.current().nextInt(1000, 9999);
            tempFile = Files.createTempFile(tempName, ".jpg");

            FFmpegBuilder builder = new FFmpegBuilder()
                    .setStartOffset((long)(midpointInSeconds * 1000), TimeUnit.MILLISECONDS) // Seek to the middle of the video BEFORE all other arguments to avoid decoding the entire video
                    .setInput(videoFile.getAbsolutePath())
                    .overrideOutputFiles(true) // Overwrite the file if it exists
                    .addOutput(tempFile.toString()) // Save to the temporary file
                    .setFrames(1) // Only one frame
                    .setVideoFilter("scale=" + resolution.getWidth() + ":" + resolution.getHeight() + ":force_original_aspect_ratio=decrease")
                    .setVideoCodec("mjpeg")
                    .addExtraArgs("-q:v", String.valueOf(qValue)) // Set jpg quality
                    .done();

            executor.createJob(builder).run();

            // Copy the temp file contents to the target stream
            Files.copy(tempFile, outputStream);

        } catch (IOException e) {
            throw new ThumbnailGenerationException("I/O error while generating thumbnail with FFMpeg", e);
        } finally {
            // Always delete the temp file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // Log the error, but do not rethrow it so the original exception is not hidden
                    System.err.println("Failed to delete temporary file: " + tempFile);
                }
            }
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return SUPPORTED_EXTENSIONS.contains(fileExtension.toLowerCase());
    }
}
