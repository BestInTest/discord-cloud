package bo.wii.discordcloud.server.cache;

import bo.wii.discordcloud.core.Logger;

import java.io.File;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static bo.wii.discordcloud.core.utils.FileHelper.formatFileSize;

public class ChunkCache {

    private final String cacheDir;
    private final long maxTotalSizeBytes;
    private final long ttlMs;
    private final ConcurrentHashMap<String, ChunkStats> stats = new ConcurrentHashMap<>();
    private final AtomicLong totalSizeBytes = new AtomicLong(0); //size of all tracked chunks
    private final ReentrantLock removalLock = new ReentrantLock();
    private final ScheduledExecutorService scheduler;

    /**
     * @param cacheDir directory where chunk files are stored. You must provide the same directory in DownloaderService
     * @param maxTotalSizeMb maximum total size of cached chunks in megabytes
     * @param ttlMinutes if a chunk is not accessed within this time, it will be deleted. Set to 0 to disable.
     */
    public ChunkCache(String cacheDir, long maxTotalSizeMb, int ttlMinutes) {
        this.cacheDir = cacheDir.endsWith("/") ? cacheDir : cacheDir + "/";
        this.maxTotalSizeBytes = maxTotalSizeMb * 1024L * 1024L;
        this.ttlMs = ttlMinutes > 0 ? ttlMinutes * 60000L : 0;

        loadExistingFiles();

        if (ttlMs > 0) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cache-ttl-cleanup");
                t.setDaemon(true);
                return t;
            });
            // cleanup 1 minute after start then every minute
            scheduler.scheduleAtFixedRate(this::cleanExpired, 1, 1, TimeUnit.MINUTES);
        } else {
            scheduler = null;
        }
    }

    /**
     * Records that a chunk has been accessed (resets its TTL)
     *
     * @param filename the chunk filename (not full path)
     * @param fileSize the size of the chunk in bytes
     */
    public void recordAccess(String filename, long fileSize) {
        ChunkStats existing = stats.get(filename);
        if (existing != null) {
            existing.recordAccess();
        } else {
            ChunkStats fresh = new ChunkStats(filename, fileSize);
            ChunkStats race = stats.putIfAbsent(filename, fresh);
            if (race == null) {
                totalSizeBytes.addAndGet(fileSize);
                removeIfNeeded();
            } else {
                race.recordAccess();
            }
        }
    }

    /**
     * Stops tracking a chunk WITHOUT deleting the file from disk.
     * Should be called only when the file is deleted by external code.
     *
     * @param filename the chunk filename (not full path)
     */
    public void untrack(String filename) {
        ChunkStats removed = stats.remove(filename);
        if (removed != null) {
            totalSizeBytes.addAndGet(-removed.fileSize);
        }
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public long getTotalSizeBytes() {
        return totalSizeBytes.get();
    }

    public int getTrackedChunkCount() {
        return stats.size();
    }


    /**
     * Scans all tracked chunks and deletes those whose last access time is
     * older than {@code ttlMs}
     */
    private void cleanExpired() {
        long cutoff = System.currentTimeMillis() - ttlMs;
        int removed = 0;
        for (ChunkStats s : stats.values()) {
            if (s.lastAccessTime < cutoff) {
                deleteChunk(s.filename);
                removed++;
            }
        }
        if (removed > 0) {
            Logger.debug(ChunkCache.class, "TTL cleanup removed " + removed + " expired chunk(s)"
                    + " (" + formatFileSize(totalSizeBytes.get()) + " / " + formatFileSize(maxTotalSizeBytes) + " used)");
        }
    }

    /**
     * If the total size exceeds the limit, removes the least recently used
     * chunks until the cache fits limit again.
     */
    private void removeIfNeeded() {
        if (totalSizeBytes.get() <= maxTotalSizeBytes) return;
        if (!removalLock.tryLock()) return;
        try {
            while (totalSizeBytes.get() > maxTotalSizeBytes && !stats.isEmpty()) {
                // Least recently used first
                ChunkStats candidate = stats.values().stream()
                        .min(Comparator.comparingLong(s -> s.lastAccessTime))
                        .orElse(null);
                if (candidate == null) break;
                deleteChunk(candidate.filename);
            }
        } finally {
            removalLock.unlock();
        }
    }

    private void deleteChunk(String filename) {
        ChunkStats removed = stats.remove(filename);
        if (removed == null) return;

        totalSizeBytes.addAndGet(-removed.fileSize); // SUBTRACT

        File file = new File(cacheDir + filename);
        if (file.exists()) {
            if (file.delete()) {
                Logger.debug(ChunkCache.class, "Cache removed: " + filename
                        + " (" + formatFileSize(removed.fileSize) + " freed)");
            } else {
                Logger.error(ChunkCache.class, "Could not delete cached chunk: " + filename);
                file.deleteOnExit();
            }
        }
    }

    private void loadExistingFiles() {
        File dir = new File(cacheDir);
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        long count = 0;
        for (File file : files) {
            if (file.isFile() && file.length() > 0) {
                ChunkStats s = new ChunkStats(file.getName(), file.length());
                // Restore actual file modification time so TTL works correctly after restart
                s.lastAccessTime = file.lastModified();
                stats.put(file.getName(), s);
                totalSizeBytes.addAndGet(file.length());
                count++;
            }
        }

        if (count > 0) {
            Logger.info(ChunkCache.class, "Discovered " + count
                    + " existing chunk(s) on disk ("
                    + formatFileSize(totalSizeBytes.get()) + " / "
                    + formatFileSize(maxTotalSizeBytes) + " limit)");
            removeIfNeeded();
        }
    }
}
