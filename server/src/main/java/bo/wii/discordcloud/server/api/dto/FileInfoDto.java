package bo.wii.discordcloud.server.api.dto;

/**
 * single entry (file or directory) returned by /api/files endpoint.
 */
public class FileInfoDto {

    public String name;
    public long size;
    public int parts;
    public long uploadTimestamp;
    public String thumbnail;
    public boolean isDirectory;
    public String path;
}

