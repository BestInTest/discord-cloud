package bo.wii.discordcloud.server.api.dto;

import java.util.List;

public class UploadStatusDto {
    public String state; // idle, uploading, done, error
    public String fileName;
    public int currentPart;
    public int totalParts;
    public List<String> logs;
    public String error;
}

