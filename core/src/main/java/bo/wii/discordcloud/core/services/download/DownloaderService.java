package bo.wii.discordcloud.core.services.download;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.client.LinkRefresher;
import bo.wii.discordcloud.core.client.WebhookLinkRefresher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import bo.wii.discordcloud.core.structure.ChunkFileInfo;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.structure.DiscordResponse;
import bo.wii.discordcloud.core.structure.attachment.DiscordAttachment;
import bo.wii.discordcloud.core.utils.FileHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DownloaderService implements AutoCloseable {

    private final LinkRefresher linkRefresher;
    private final OkHttpClient client = new OkHttpClient();

    private LinkedList<ChunkFileInfo> loadedParts = new LinkedList<>();

    // Prefetch system
    private final int PREFETCH_COUNT = 2; // how many parts should be prefetched ahead of time
    private final int THREAD_POOL_SIZE = 2; // number of threads for prefetching

    private final boolean prefetchEnabled;
    private final ConcurrentHashMap<Integer, DiscordAttachment> prefetchedAttachments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Future<?>> prefetchTasks = new ConcurrentHashMap<>();
    private final ExecutorService prefetchExecutor;
    private volatile int currentPartIndex = 0;
    private volatile String downloadPath = null;

    @Deprecated
    public DownloaderService(FileStruct structure, String webhook, boolean enablePrefetch) {
        this(structure, new WebhookLinkRefresher(webhook), enablePrefetch);
    }

    public DownloaderService(FileStruct structure, LinkRefresher linkRefresher, boolean enablePrefetch) {
        this.linkRefresher = linkRefresher;
        this.prefetchEnabled = enablePrefetch;

        // Initialize executor only when prefetch is enabled
        this.prefetchExecutor = enablePrefetch ? Executors.newFixedThreadPool(THREAD_POOL_SIZE) : null;

        if (!structure.isValid()) {
            throw new IllegalArgumentException("Invalid structure");
        }

        if (!FileHelper.extractBadParts(structure.getParts()).isEmpty()) {
            throw new IllegalArgumentException("Found bad parts in structure");
        }

        loadedParts = new LinkedList<>(structure.getParts());
        //TODO: sprawdzić czy kolejność plików jest w porządku

        if (prefetchEnabled) {
            Logger.debug(DownloaderService.class, "Prefetch is enabled");
        } else {
            Logger.debug(DownloaderService.class, "Prefetch is disabled");
        }
    }

    /**
     * Initializes prefetch for the first few parts
     */
    private void initializePrefetch() {
        if (!prefetchEnabled) return;

        // Math.min() because there may be fewer parts than PREFETCH_COUNT
        for (int i = 0; i < Math.min(PREFETCH_COUNT, loadedParts.size()); i++) {
            startPrefetchForPart(i);
        }
    }

    /**
     * Starts prefetch for a specific part in the background
     */
    private void startPrefetchForPart(int partNumber) {
        if (!prefetchEnabled) return;

        if (partNumber >= loadedParts.size()) {
            return;
        }

        // Check if already in progress or ready
        if (prefetchedAttachments.containsKey(partNumber) || prefetchTasks.containsKey(partNumber)) {
            return;
        }

        // If file is on disk, there is no need to refresh the link
        if (downloadPath != null) {
            ChunkFileInfo fileInfo = loadedParts.get(partNumber);
            File existing = new File(downloadPath + fileInfo.getName());
            if (existing.exists() && existing.length() > 0) {
                return;
            }
        }

        Future<?> task = prefetchExecutor.submit(() -> {
            try {
                ChunkFileInfo fileInfo = loadedParts.get(partNumber);
                long id = Long.parseLong(fileInfo.getMessageId());

                Logger.debug(DownloaderService.class, "Prefetching part " + partNumber + ": " + fileInfo.getName());

                DiscordResponse response = linkRefresher.refreshLinks(id, null);
                List<DiscordAttachment> att = response.getAttachments();

                if (att != null && !att.isEmpty() && att.size() == 1) {
                    prefetchedAttachments.put(partNumber, att.get(0));
                    Logger.debug(DownloaderService.class, "Prefetch completed for part " + partNumber);
                } else {
                    Logger.error(DownloaderService.class, "Prefetch failed for part " + partNumber);
                }
            } catch (Exception e) {
                Logger.error(DownloaderService.class, "Exception during prefetch for part " + partNumber + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                prefetchTasks.remove(partNumber);
            }
        });

        prefetchTasks.put(partNumber, task);
    }

    /**
     * Waits until the part is ready (if it is currently being prefetched)
     */
    private DiscordAttachment waitForPrefetchedAttachment(int partNumber) {
        if (!prefetchEnabled) return null;

        // Return immediately if the prefetch for this part has already finished
        if (prefetchedAttachments.containsKey(partNumber)) {
            return prefetchedAttachments.get(partNumber);
        }

        // wait if in progress
        Future<?> task = prefetchTasks.get(partNumber);
        if (task != null) {
            try {
                Logger.debug(DownloaderService.class, "Waiting for prefetch to complete for part " + partNumber);
                task.get(); // wait until complete
                return prefetchedAttachments.get(partNumber);
            } catch (Exception e) {
                Logger.error(DownloaderService.class, "Error waiting for prefetch: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * Triggers prefetch for the next parts
     */
    private void triggerNextPrefetch(int currentPart) {
        if (!prefetchEnabled) return;

        currentPartIndex = currentPart;

        // Start prefetch for the next PREFETCH_COUNT parts
        for (int i = 1; i <= PREFETCH_COUNT; i++) {
            int nextPart = currentPart + i;
            if (nextPart < loadedParts.size()) {
                startPrefetchForPart(nextPart);
            }
        }

        // Remove old cache entries (parts that will no longer be needed)
        cleanupOldCache(currentPart);
    }

    /**
     * Removes from cache parts that are already far behind the current position
     */
    private void cleanupOldCache(int currentPart) {
        prefetchedAttachments.keySet().removeIf(key -> key < currentPart - 2);
    }

    //fixme: naprawić działanie niewygasłych linków bo obecnie zawsze są odświeżane
    public File downloadPart(int partNumber, String path) {
        //ustawianie ścieżki przy pierwszym wywołaniu i dopiero wtedy init prefetch (lazy init)
        if (downloadPath == null) {
            downloadPath = path;
            if (prefetchEnabled) {
                initializePrefetch();
            }
        }

        ChunkFileInfo fileInfo = loadedParts.get(partNumber);

        File alreadyDownloaded = new File(path + fileInfo.getName());
        if (alreadyDownloaded.exists()) {
            if (alreadyDownloaded.length() == 0) {
                // file is empty probably because previous downloading was force stopped
                Logger.debug(DownloaderService.class, "Part " + partNumber + " exists but is empty. Deleting and re-downloading...");
                alreadyDownloaded.delete();
            } else {
                // skip prefetch for already downloaded part and go to next
                Logger.debug(DownloaderService.class, "Part " + partNumber + " already exists. Skipping...");
                triggerNextPrefetch(partNumber); // przesuwa okno prefetch do przodu, bo inaczej skip nie wyzwoli prefetch dla kolejnych części
                return alreadyDownloaded;
            }
        }

        // Check if attachment is already prefetched
        DiscordAttachment attachment = waitForPrefetchedAttachment(partNumber);

        // If not prefetched or prefetch failed, fetch now
        if (attachment == null) {
            Logger.debug(DownloaderService.class, "Attachment not prefetched, fetching now for part " + partNumber);
            long id = Long.parseLong(fileInfo.getMessageId());
            DiscordResponse response = linkRefresher.refreshLinks(id, null);
            List<DiscordAttachment> att = response.getAttachments();

            if (att == null || att.isEmpty()) {
                Logger.error(DownloaderService.class, "Failed to download part: " + fileInfo.getName());
                return null;
            }
            if (att.size() != 1) {
                Logger.error(DownloaderService.class, "Message " + fileInfo.getMessageId() + " has more than one attachment: " + att.size());
                return null;
            }
            attachment = att.get(0);
        }

        // Remove from cache after use
        prefetchedAttachments.remove(partNumber);

        // Trigger prefetch for the next parts
        triggerNextPrefetch(partNumber);

        return downloadAttachment(attachment, path, fileInfo.getName());
    }

    /**
     * Shuts down the executor service
     */
    public void shutdown() {
        if (prefetchEnabled && prefetchExecutor != null) {
            Logger.debug(DownloaderService.class, "Shutting down prefetch executor");
            prefetchExecutor.shutdown();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private File downloadAttachment(DiscordAttachment attachment, String tempDir, String expectedFilename) {
        String url = attachment.getUrl();
        Logger.debug(DownloaderService.class, "Downloading attachment: " + attachment.getFilename());

        File out = new File(tempDir);
        out.mkdirs(); // create temp dir for downloading files

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        int responseCode = -1;
        boolean success = false;

        // Retry up to 5 times until success
        for (int attemps = 0; (attemps < 5) && (!success); attemps++) {
            try (Response response = client.newCall(request).execute()) {
                responseCode = response.code();
                Logger.debug(DownloaderService.class, "Response code: " + responseCode);

                //too many requests
                if (responseCode == 429) {
                    // Wait for rate limit to reset and retry
                    Thread.sleep(5000); // 5s might not be enough
                    continue;
                }

                //Gateway unavailable
                if (responseCode == 502) {
                    /*
                     Według dokumentacji wystarczy poczekać i spróbować ponownie.
                     https://discord.com/developers/docs/topics/opcodes-and-status-codes#http
                    */
                    Thread.sleep(3000);
                    continue;
                }

                if (responseCode == 404) {
                    throw new FileNotFoundException("File not found: HTTP code 404");
                }

                if (responseCode == 200 || responseCode == 201) {
                    success = true;

                    // Discord can remove non-english characters from filename
                    // which can cause issues with resuming downloads because the file on disk won't match the expected name
                    out = new File(tempDir + expectedFilename); // change variable to output file path

                    // Save file to disk
                    try (FileOutputStream fileOutputStream = new FileOutputStream(out)) {
                        fileOutputStream.write(response.body().bytes());
                    }
                    return out;
                }

                //nieznany błąd pomimo prób pobierania
                Logger.error(DownloaderService.class, "Wystąpił nieznany błąd HTTP: " + responseCode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
