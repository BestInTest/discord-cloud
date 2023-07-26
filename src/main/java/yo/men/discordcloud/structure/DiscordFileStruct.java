package yo.men.discordcloud.structure;

import yo.men.discordcloud.Main;

import java.io.File;
import java.util.LinkedList;

public class DiscordFileStruct {
    //private final String structFileName;
    private final String originalFileName; //nazwa pliku
    //TODO: fileSize (pełna waga pliku, może w bajtach?) - pozwoli to później na łatwe pokazywanie wielkości pliku w gui
    private final long fileSize;
    private final String fixedFilePath; // todo: do usunięcia
    private final String sha256Hash; // hash pełnego pliku
    private LinkedList<DiscordFilePart> parts;

    public DiscordFileStruct(String originalFileName, String hash, LinkedList<DiscordFilePart> parts) {
        File f = new File(originalFileName);
        this.originalFileName = f.getName();
        //this.originalFileName =
        this.fileSize = f.length();
        this.fixedFilePath = originalFileName.replace(System.getProperty("user.dir"), Main.STORAGE_DIR);
        this.sha256Hash = hash;
        this.parts = parts;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFixedFilePath() { // Zwraca ścieżkę z "bazy danych" programu (dzięki temu program może być przenoszony)
        return fixedFilePath;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public LinkedList<DiscordFilePart> getParts() {
        return parts;
    }

    public void setParts(LinkedList<DiscordFilePart> parts) {
        this.parts = parts;
    }
}
