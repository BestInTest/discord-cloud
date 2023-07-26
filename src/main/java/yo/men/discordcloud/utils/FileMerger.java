package yo.men.discordcloud.utils;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;

public class FileMerger {
    //fixme: przy duzych plikach (duza ilosc kawalkow) plik jest zle skladany
    public static void mergeFiles(String folderPath, File outputFile) throws IOException {
        File folder = new File(folderPath);
        File[] partFiles = folder.listFiles((dir, name) -> name.matches(".+\\.part\\d+"));

        if (partFiles == null || partFiles.length == 0) {
            System.out.println("Brak plików do scalenia w folderze: " + folder.getAbsolutePath());
            return;
        }

        Arrays.sort(partFiles, Comparator.comparing(File::getName));

        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            for (File partFile : partFiles) {
                try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(partFile))) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                // Usunięcie pliku częściowego
                if (!partFile.delete()) {
                    System.out.println("Nie można usunąć pliku: " + partFile.getAbsolutePath());
                }
            }
        }

        System.out.println("Pliki zostały scalone w plik: " + outputFile);
    }

    private static void sortPartFiles(File[] partFiles) {
        // Sortowanie plików wg numeru części
        // Przykład sortowania: part1, part2, part3, ...
        for (int i = 0; i < partFiles.length - 1; i++) {
            for (int j = i + 1; j < partFiles.length; j++) {
                if (extractPartNumber(partFiles[j]) < extractPartNumber(partFiles[i])) {
                    File temp = partFiles[i];
                    partFiles[i] = partFiles[j];
                    partFiles[j] = temp;
                }
            }
        }
    }

    private static int extractPartNumber(File file) {
        String fileName = file.getName();
        int startIndex = fileName.lastIndexOf("part") + 4;
        int endIndex = fileName.lastIndexOf(".");
        String partNumberString = fileName.substring(startIndex, endIndex);

        return Integer.parseInt(partNumberString);
    }
}
