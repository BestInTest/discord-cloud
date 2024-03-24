package yo.men.discordcloud.utils;

//import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import yo.men.discordcloud.Main;
import yo.men.discordcloud.structure.DiscordFilePart;
import yo.men.discordcloud.structure.DiscordFileStruct;

import java.io.*;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class FileHelper {

    private static final int MAX_FILE_SIZE = Main.CHUNK_FILE_SIZE;
    private static final String TEMP_DIR_PATH = ".temp/";

    // Sprawdza, czy plik jest podzielony
    //Zmienić na isChunkFile?
    public static boolean isSplitFile(String fileName) {
        return fileName.matches(".+\\.part\\d+$");
    }

    //Pierwszy kawałek powinien mieć partNumber = 0
    public static File getFilePart(String filePath, int partNumber, long chunkSize) throws IOException {

        long maxParts = calculateMaxPartCount(filePath, Main.CHUNK_FILE_SIZE);
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

            new File(TEMP_DIR_PATH).mkdirs(); // tworzenie folderu tymczasowego, jeżeli nie istnieje
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                while ((bytesRead = randomAccessFile.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (totalBytesRead >= chunkSize) {
                        break;
                    }
                }
            }

            System.out.println("Part " + partNumber + " created: " + outputFile);
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

    //structureFile w moim rozumieniu to json z danymi o kawałkach pliku itp
    public static File toStructureFile(File originalFile) {
        if (!originalFile.getAbsolutePath().endsWith(".json")) {
            return new File(originalFile.getAbsolutePath() + ".json");
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

    public static void saveUploadedFile(File partFile, File originalFile, String messageId, String url, boolean isSuccess) throws IOException {
        DiscordFileStruct structure = FileHelper.loadStructureFile(new File(originalFile.getName() + ".json"));
        if (structure == null) {
            structure = new DiscordFileStruct(originalFile.getAbsolutePath(), FileHashCalculator.getFileHash(originalFile), new LinkedHashSet<>());
        }
        LinkedHashSet<DiscordFilePart> uploadedFiles = structure.getParts();

        String hash = FileHashCalculator.getFileHash(partFile);
        DiscordFilePart oldBadPart = getBadPart(partFile.getName(), uploadedFiles); // kawałek który nie mógł zostać wysłany
        DiscordFilePart newPart = new DiscordFilePart(partFile.getName(), hash, messageId, url, isSuccess);

        /*
        Jeżeli jest wysyłany jakiś kawałek pliku, który nie mógł zostać
        wcześniej wysłany, to trzeba usunąć go z listy kawałków i dodać ten poprawny.
         */
        if (oldBadPart != null) {
            uploadedFiles.remove(oldBadPart);
        }
        uploadedFiles.add(newPart);

        saveStructure(structure, uploadedFiles);
    }

    public static void saveStructure(DiscordFileStruct structure, LinkedHashSet<DiscordFilePart> uploadedFiles) {
        structure.setParts(uploadedFiles);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(structure);

        File targetStructFile = new File(structure.getOriginalFileName() + ".json");
        try (Writer writer = new FileWriter(targetStructFile)) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static DiscordFileStruct loadStructureFile(File file) throws IOException {
        System.out.println("loading file " + file.getAbsolutePath());

        File structureFile = FileHelper.toStructureFile(file); //structureFile w moim rozumieniu to json z danymi o kawałkach pliku itp
        try (Reader reader = new FileReader(structureFile)) {
            Gson gson = new Gson();
            DiscordFileStruct dscFile = gson.fromJson(reader, DiscordFileStruct.class);
            if (dscFile != null) {
                return dscFile;
            }
        } catch (FileNotFoundException e) { //Struktura jeszcze nie istnieje - wysyłanie pierwszego kawałka
            return null;
        }

        //System.out.println("loaded " + f.getParts().size() + " parts");
        return null;
    }

    public static DiscordFilePart getBadPart(String partFileName, LinkedHashSet<DiscordFilePart> partsList) {
        for (DiscordFilePart part : partsList) {
            if (part.getName().equals(partFileName)) {
                return part;
            }
        }
        return null;
    }

    public static Set<DiscordFilePart> extractBadParts(LinkedHashSet<DiscordFilePart> allParts) {
        Set<DiscordFilePart> badParts = new HashSet<>();
        for (DiscordFilePart part : allParts) {
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
}
