package bo.wii.discordcloud.core.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

public class Base64Util {

    /**
     * Encodes a file to a Base64 string.
     *
     * @param file File to encode.
     * @return A Base64-encoded string representing the file's content.
     * @throws IOException If the file cannot be read.
     */
    public static String fileToBase64(File file) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(fileBytes);
    }

    /**
     * Decodes a Base64 string and writes the result to a file.
     *
     * @param base64Content The Base64 string to decode.
     * @param outputFilePath The path where the decoded file will be saved.
     * @throws IOException If the file cannot be written.
     */
    public static void fileFromBase64(String base64Content, String outputFilePath) throws IOException {
        byte[] decodedBytes = Base64.getDecoder().decode(base64Content);
        Files.write(new File(outputFilePath).toPath(), decodedBytes);
    }

    public static String bytesToBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
