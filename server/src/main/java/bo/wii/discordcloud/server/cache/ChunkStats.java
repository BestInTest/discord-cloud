package bo.wii.discordcloud.server.cache;

public class ChunkStats {

    final String filename;
    final long fileSize;
    volatile long lastAccessTime;

    ChunkStats(String filename, long fileSize) {
        this.filename = filename;
        this.fileSize = fileSize;
        this.lastAccessTime = System.currentTimeMillis();
    }

    void recordAccess() {
        lastAccessTime = System.currentTimeMillis();
    }
}
