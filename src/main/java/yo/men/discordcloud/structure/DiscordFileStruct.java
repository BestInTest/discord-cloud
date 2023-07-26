package yo.men.discordcloud.structure;

import yo.men.discordcloud.Main;

import java.io.File;
import java.util.LinkedList;

public class DiscordFileStruct {

    //todo: fileVersion
    private final String originalFileName; //nazwa pliku
    //TODO: fileSize (pełna waga pliku, może w bajtach?) - pozwoli to później na łatwe pokazywanie wielkości pliku w gui
    private final long fileSize;
    private final String sha256Hash; // hash pełnego pliku
    private final int singlePartSize;
    private LinkedList<DiscordFilePart> parts;

    public DiscordFileStruct(String originalFileName, String hash, LinkedList<DiscordFilePart> parts) {
        File f = new File(originalFileName);
        this.originalFileName = f.getName();
        this.fileSize = f.length();
        this.sha256Hash = hash;
        this.singlePartSize = Main.MAX_FILE_SIZE; // nie wiem czy to będzie poprawnie działać i czy nie lepiej dać argument z konstruktora
        this.parts = parts;
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

    public LinkedList<DiscordFilePart> getParts() {
        return parts;
    }

    public void setParts(LinkedList<DiscordFilePart> parts) {
        this.parts = parts;
    }
}
