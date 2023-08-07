package yo.men.discordcloud.utils;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileMerger {
    //fixme: przy duzych plikach (duza ilosc kawalkow) plik jest zle skladany
    public static void mergeFiles(String folderPath, File outputFile) throws IOException {
        File folder = new File(folderPath);
        File[] partFiles = folder.listFiles((dir, name) -> name.matches(".+\\.part\\d+"));

        if (partFiles == null || partFiles.length == 0) {
            System.out.println("Brak plików do scalenia w folderze: " + folder.getAbsolutePath());
            return;
        }

        //todo: przetestować dla pliku +100 kawałków (można zmienić maxpartsize)
        Arrays.sort(partFiles, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                Pattern pattern = Pattern.compile("\\.part(\\d+)$");
                Matcher matcher1 = pattern.matcher(file1.getName());
                Matcher matcher2 = pattern.matcher(file2.getName());

                if (matcher1.find() && matcher2.find()) {
                    int partNum1 = Integer.parseInt(matcher1.group(1));
                    int partNum2 = Integer.parseInt(matcher2.group(1));
                    return Integer.compare(partNum1, partNum2);
                } else {
                    // Filenames don't match the expected pattern
                    return 0; // Default comparison (no sorting) if unable to extract part numbers
                }
            }
        });

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
