package bo.wii.discordcloud.core;

import bo.wii.discordcloud.core.services.ThumbnailService;

public class DiscordCloudCore {

    /**
     * Discord for some reason doesn't accept files that have exact size as limit.
     * So we define SCALAR as 1 MB - 1 KB to make sure we are always under the limit.
     */
    public static final int MB_SCALAR = 1024 * 1023; // ~1 MB

    public static final int CHUNK_FILE_SIZE = 10 * MB_SCALAR; // 10 MB
    public static final ThumbnailService thumbnailService = new ThumbnailService(DiscordCloudCore.class);
}
