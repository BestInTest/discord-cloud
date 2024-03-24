package yo.men.discordcloud.structure;

import yo.men.discordcloud.Main;

import java.io.File;
import java.util.LinkedHashSet;

public class DiscordFileStruct {

    private final int fileVersion; // obecnie nie ma zastosowania, może się przydać w przyszłości
    private final String originalFileName; //nazwa pliku
    private final long fileSize;
    private final String sha256Hash; // hash pełnego pliku
    private final int singlePartSize;
    private LinkedHashSet<DiscordFilePart> parts;

    public DiscordFileStruct(String originalFileName, String hash, LinkedHashSet<DiscordFilePart> parts) {
        this.fileVersion = 1;
        File f = new File(originalFileName);
        this.originalFileName = f.getName();
        this.fileSize = f.length();
        this.sha256Hash = hash;
        this.singlePartSize = Main.CHUNK_FILE_SIZE; // nie wiem czy to będzie poprawnie działać i czy nie lepiej dać argument z konstruktora
        this.parts = parts;
    }

    public int getFileVersion() {
        return fileVersion;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public int getSinglePartSize() {
        return singlePartSize;
    }

    public LinkedHashSet<DiscordFilePart> getParts() {
        return parts;
    }

    public void setParts(LinkedHashSet<DiscordFilePart> parts) {
        this.parts = parts;
    }

    /*
    Sprawdza poprawność wartości.
    Można użyć kiedy nie ma się pewności czy załadowany json
    ma strukturę tej klasy.
     */
    public boolean isValid() {
        return (fileVersion > 0) && (fileSize > 0) && (singlePartSize > 0) && (originalFileName != null) && (sha256Hash != null) && (parts != null) && (!parts.isEmpty());
    }

    //Sprawdza czy żaden link nie stracił ważności
    public boolean isExpired() {
        for (DiscordFilePart part : parts) {
            if (part.getAttachmentAuth().isExpired()) {
                return true;
            }
        }
        return false;
    }
}
