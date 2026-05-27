package bo.wii.discordcloud.core.utils;

//import com.fasterxml.jackson.databind.ObjectMapper;
import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.structure.enums.UploadType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import bo.wii.discordcloud.core.DiscordCloudCore;
import bo.wii.discordcloud.core.structure.ChunkFileInfo;
import bo.wii.discordcloud.core.structure.FileStruct;

import java.io.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class FileHelper {

    private static final String TEMP_DIR_PATH = ".temp/";

    public static final String STRUCTURE_EXTENSION = ".dscl";
    public static final String LEGACY_STRUCTURE_EXTENSION = ".json"; // for backward compatibility

    // Sprawdza, czy plik jest podzielony
    //Zmienić na isChunkFile?
    public static boolean isSplitFile(String fileName) {
        return fileName.matches(".+\\.part\\d+$");
    }

    // First chunk should have partNumber = 0
    public static File getFilePart(String filePath, int partNumber, long chunkSize) throws IOException {

        long maxParts = calculateMaxPartCount(filePath, DiscordCloudCore.CHUNK_FILE_SIZE);
        if (partNumber < 0 || partNumber >= maxParts) {
            throw new IndexOutOfBoundsException("Provided part number (" + partNumber + ") is out of file range (0-" + (maxParts-1) + ")");
        }

        long startPosition = (long) partNumber * chunkSize;

        File file = new File(filePath);
        String fileName = file.getName();
        String outputFile = TEMP_DIR_PATH + fileName + ".part" + partNumber;

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            randomAccessFile.seek(startPosition);

            byte[] buffer = new byte[1024];
            int bytesRead;
            int totalBytesRead = 0;

            new File(TEMP_DIR_PATH).mkdirs(); // create temp directory if it does not exist
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                while ((bytesRead = randomAccessFile.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (totalBytesRead >= chunkSize) {
                        break;
                    }
                }
            }

            Logger.debug(FileHelper.class, "Part " + partNumber + " created: " + outputFile);
        }

        return new File(outputFile);
    }

    public static long calculateMaxPartCount(String filePath, long chunkSize) {
        File file = new File(filePath);
        long fileSize = file.length();

        long maxPartCount = fileSize / chunkSize;
        if (fileSize % chunkSize != 0) {
            maxPartCount++;
        }

        return maxPartCount;
    }

    /**
     * Converts a given file into a structure file by appending '.dscl' extension
     * if it doesn't already have a known structure extension ('.dscl' or '.json').
     *
     * @param originalFile The file to convert.
     * @return The converted file.
     */
    public static File toStructureFile(File originalFile) {
        String path = originalFile.getAbsolutePath();
        if (!path.endsWith(STRUCTURE_EXTENSION) && !path.endsWith(LEGACY_STRUCTURE_EXTENSION)) {
            return new File(path + STRUCTURE_EXTENSION);
        }
        return originalFile;
    }

    public static String removeExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int extensionIndex = filename.lastIndexOf(".");
        if (extensionIndex == -1) {
            return filename;
        }
        return filename.substring(0, extensionIndex);
    }

    //deprecated? uzywane do uploadu przez webhooka to moze zostawic albo jakos zmienic
    public static void saveUploadedFile(File partFile, File originalFile, String messageId, String url, boolean isSuccess, int chunkSize) throws IOException {
        saveUploadedFile(partFile, originalFile, messageId, url, isSuccess, null, null, chunkSize);
    }

    public static void saveUploadedFile(File partFile, File originalFile, String messageId, String url, boolean isSuccess,
                                        UploadType uploadType, String channelId, int chunkSize) throws IOException {
        FileStruct structure = FileHelper.loadStructureFile(new File(originalFile.getName()));
        if (structure == null) {
            String hash = FileHashCalculator.getFileHash(originalFile);
            structure = new FileStruct(originalFile.getAbsolutePath(), hash, new LinkedHashSet<>(), uploadType, channelId, chunkSize);
        } else {
            //TODO: sprawdzić tą zmianę channelId i uploadType bo trzeba zrobić tak żeby były stałe (channelId i uploadType NIE MOGĄ się zmieniać)

            // Update uploadType and channelId if provided
            if (uploadType != null) {
                structure.setUploadType(uploadType);
            }
            if (channelId != null) {
                structure.setChannelId(channelId);
            }
        }
        LinkedHashSet<ChunkFileInfo> uploadedFiles = structure.getParts();

        String hash = FileHashCalculator.getFileHash(partFile);
        ChunkFileInfo oldBadPart = getBadPart(partFile.getName(), uploadedFiles); // chunk that failed to upload previously
        ChunkFileInfo newPart = new ChunkFileInfo(partFile.getName(), hash, messageId, url, isSuccess);

        /*
        If a chunk is being re-uploaded that previously failed to upload,
        it must be removed from the parts list and replaced with the correct one.
         */
        if (oldBadPart != null) {
            uploadedFiles.remove(oldBadPart);
        }
        uploadedFiles.add(newPart);

        saveStructureParts(structure, uploadedFiles);
    }

    public static void saveStructureParts(FileStruct structure, LinkedHashSet<ChunkFileInfo> uploadedFiles) {
        structure.setParts(uploadedFiles);
        saveStructureToFile(structure);
    }

    public static void saveThumbnailToStructure(FileStruct structure, String base64Thumbnail) {
        if (structure == null) {
            Logger.err(FileHelper.class, "Cannot save thumbnail: structure is null");
            return;
        }
        structure.setThumbnailBase64(base64Thumbnail);
        saveStructureToFile(structure);
    }

    private static void saveStructureToFile(FileStruct structure) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(structure);

        File targetStructFile = new File(structure.getOriginalFileName() + STRUCTURE_EXTENSION);
        try (Writer writer = new FileWriter(targetStructFile)) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads a {@link FileStruct} from a structure file.
     * <p>
     * If the passed file does not already have a known structure extension,
     * the method first tries to find a {@code .dscl} file, then falls back to
     * the legacy {@code .json} extension for backward compatibility.
     * Returns {@code null} if neither file exists or if the JSON is empty.
     *
     * @param file the file to load the structure from (with or without extension)
     * @return the loaded structure, or {@code null} if the file does not exist or
     *     contains invalid data
     * @throws IOException if an I/O error occurs while reading the file, or if
     *     the file structure version is not supported
     */
    public static FileStruct loadStructureFile(File file) throws IOException {
        Logger.debug(FileHelper.class, "loading file " + file.getAbsolutePath());

        File structureFile = resolveStructureFile(file);
        try (Reader reader = new FileReader(structureFile)) {
            Gson gson = new Gson();
            FileStruct dscFile = gson.fromJson(reader, FileStruct.class);
            if (dscFile != null) {
                if (dscFile.getFileVersion() < FileStruct.MIN_SUPPORTED_VERSION) {
                    throw new IOException("Unsupported file structure version: " + dscFile.getFileVersion()
                            + ". Minimum supported version is " + FileStruct.MIN_SUPPORTED_VERSION);
                }
                if (dscFile.getFileVersion() > FileStruct.CURRENT_VERSION) {
                    throw new IOException("Unsupported file structure version: " + dscFile.getFileVersion()
                            + ". Maximum supported version is " + FileStruct.CURRENT_VERSION
                            + ". Please update the application.");
                }
                return dscFile;
            }
        } catch (FileNotFoundException e) { // Structure does not exist yet - uploading the first chunk
            return null;
        }

        //System.out.println("loaded " + f.getParts().size() + " parts");
        return null;
    }

    /**
     * Resolves the actual structure file to read.
     * <p>
     * If {@code file} already ends with a known extension ({@code .dscl} or
     * {@code .json}), it is returned as-is. Otherwise the method checks for a
     * {@code .dscl} file first, if it doesn't exist it falls back to
     * {@code .json}. When neither exists, the {@code .dscl} candidate is
     * returned (caller will get a {@link FileNotFoundException}).
     */
    private static File resolveStructureFile(File file) {
        String path = file.getAbsolutePath();
        // File already has a known extension
        if (path.endsWith(STRUCTURE_EXTENSION) || path.endsWith(LEGACY_STRUCTURE_EXTENSION)) {
            return file;
        }

        // Try new .dscl extension first
        File dsclFile = new File(path + STRUCTURE_EXTENSION);
        if (dsclFile.exists()) {
            return dsclFile;
        }

        // Try legacy .json
        File jsonFile = new File(path + LEGACY_STRUCTURE_EXTENSION);
        if (jsonFile.exists()) {
            return jsonFile;
        }

        // Not found. Return .dscl so the caller gets FileNotFoundException -> null
        return dsclFile;
    }

    public static ChunkFileInfo getBadPart(String partFileName, LinkedHashSet<ChunkFileInfo> partsList) {
        for (ChunkFileInfo part : partsList) {
            if (part.getName().equals(partFileName)) {
                return part;
            }
        }
        return null;
    }

    public static Set<ChunkFileInfo> extractBadParts(LinkedHashSet<ChunkFileInfo> allParts) {
        Set<ChunkFileInfo> badParts = new HashSet<>();
        for (ChunkFileInfo part : allParts) {
            if (!part.isSuccess()) {
                badParts.add(part);
            }
        }
        return badParts;
    }

    public static void deleteDirectory(File path){
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        path.delete();
    }

    /**
     * Gets the file extension from a given file.
     * @param file the file to get the extension from
     * @return the file extension, or an empty string if the file has no extension
     */
    public static String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return name.substring(lastIndexOf + 1);
    }

    /**
     * Format file size in human-readable format
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Formats speed in human-readable format
     */
    public static String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return String.format("%.2f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.2f KB/s", bytesPerSecond / 1024);
        } else if (bytesPerSecond < 1024 * 1024 * 1024) {
            return String.format("%.2f MB/s", bytesPerSecond / (1024 * 1024));
        } else {
            return String.format("%.2f GB/s", bytesPerSecond / (1024 * 1024 * 1024));
        }
    }

    public static String formatEta(long totalSeconds) {
        if (totalSeconds < 0) return "--";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "--";
        Instant instant = Instant.ofEpochMilli(timestamp);
        ZonedDateTime dateTime = instant.atZone(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd | HH:mm:ss");
        return dateTime.format(formatter);
    }

    /**
     * Creates a temporary file for storing a thumbnail image.
     * The file will have a random name in the format "thumb-XXXX.jpg",
     * where "XXXX" is a random number between 1000 and 9999.
     * <br>
     * File is NOT deleted on VM exit. You must delete it manually.
     *
     * @return A File object representing the created temporary thumbnail file.
     * @throws IOException If an I/O error occurs during file creation.
     */
    public static File createTempThumbnailFile() throws IOException {
        String name = "thumb-" + ThreadLocalRandom.current().nextInt(1000, 9999);
        return File.createTempFile(name, ".jpg");
    }
}
