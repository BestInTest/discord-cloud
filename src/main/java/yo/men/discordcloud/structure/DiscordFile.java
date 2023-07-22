package yo.men.discordcloud.structure;

import yo.men.discordcloud.Main;
import yo.men.discordcloud.utils.FileHelper;

import java.io.File;
import java.util.List;

public class DiscordFile {
    private final String originalName; //nazwa pliku
    //TODO: fileSize (pełna waga pliku, może w bajtach?) - pozwoli to później na łatwe pokazywanie wielkości pliku w gui
    private final long fileSize;
    private final String fixedFilePath;
    private final String sha256Hash; // hash pełnego pliku
    private List<DiscordFilePart> parts;

    public DiscordFile(String structureFilePath, String hash, List<DiscordFilePart> parts) {
        File f = new File(structureFilePath);
        this.originalName = f.getName();
        this.fileSize = f.length();
        this.fixedFilePath = structureFilePath.replace(System.getProperty("user.dir"), Main.STORAGE_DIR);
        this.sha256Hash = hash;
        this.parts = parts;
    }

    public String getOriginalName() {
        return originalName;
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

    public List<DiscordFilePart> getParts() {
        return parts;
    }

    public void setParts(List<DiscordFilePart> parts) {
        this.parts = parts;
    }
}
