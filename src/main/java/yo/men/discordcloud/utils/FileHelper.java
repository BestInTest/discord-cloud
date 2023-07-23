package yo.men.discordcloud.utils;

import com.google.gson.Gson;
import yo.men.discordcloud.Main;
import yo.men.discordcloud.structure.DiscordFileStruct;

import java.io.*;

public class FileHelper {

    private static final int MAX_FILE_SIZE = Main.MAX_FILE_SIZE;
    private static final String TEMP_DIR_PATH = ".temp/";

    // Sprawdza, czy plik jest podzielony
    public static boolean isSplitFile(String fileName) {
        return fileName.matches(".+\\.part\\d+$");
    }

    //Pierwszy kawałek powinien mieć partNumber = 0
    public static File getFilePart(String filePath, int partNumber) throws IOException {

        long maxParts = calculateMaxPartCount(filePath);
        if (partNumber < 0 || partNumber >= maxParts) {
            throw new IndexOutOfBoundsException("Provided part number (" + partNumber + ") is out of file range (0-" + (maxParts-1) + ")");
        }

        int chunkSize = MAX_FILE_SIZE; // Rozmiar części
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

    public static long calculateMaxPartCount(String filePath) {
        File file = new File(filePath);
        long fileSize = file.length();
        long chunkSize =  MAX_FILE_SIZE; // Rozmiar części

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

    public static DiscordFileStruct loadStructureFile(File file) {
        DiscordFileStruct f = null;
        System.out.println("loading file " + file.getAbsolutePath());

        File structureFile = FileHelper.toStructureFile(file); //structureFile w moim rozumieniu to json z danymi o kawałkach pliku itp
        try (Reader reader = new FileReader(structureFile)) {
            Gson gson = new Gson();
            DiscordFileStruct dscFile = gson.fromJson(reader, DiscordFileStruct.class);
            if (dscFile != null) {
                f = dscFile;
            }
        } catch (FileNotFoundException e) { //Struktura jeszcze nie istnieje, - wysyłanie pierwszego kawałka
            //Tworzenie nowej struktury pliku
            return f;
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("loaded " + f.getParts().size() + " parts");
        return f;
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
