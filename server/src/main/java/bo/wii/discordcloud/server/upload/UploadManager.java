package bo.wii.discordcloud.server.upload;

import bo.wii.discordcloud.core.DiscordCloudCore;
import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.services.upload.UploadBotTask;
import bo.wii.discordcloud.core.services.upload.UploadProgressCallback;
import bo.wii.discordcloud.core.services.upload.UploadTask;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.server.api.dto.UploadStatusDto;
import bo.wii.discordcloud.server.config.ServerConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class UploadManager {

    private enum State {
        IDLE,
        UPLOADING,
        DONE,
        ERROR
    }

    private static class Status {
        volatile State state = State.IDLE;
        volatile String fileName;
        volatile int currentPart;
        volatile int totalParts;
        final List<String> logs = new CopyOnWriteArrayList<>();
        volatile String error;
    }

    private volatile Status current = new Status();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final ServerConfig config;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "upload-worker");
        t.setDaemon(true);
        return t;
    });

    public UploadManager(ServerConfig config) {
        this.config = config;
    }

    /**
     * Start upload. Returns false if an upload is already in progress.
     *
     * @param tempFile the temporary file already saved to disk
     * @param destPath relative path inside filesDirectory where .dscl should be saved
     * @param uploadType "WEBHOOK" or "BOT"
     * @param chunkSizeMb chunk size in MB (used only for BOT mode)
     */
    public synchronized boolean startUpload(File tempFile, String destPath, String uploadType, int chunkSizeMb) {
        if (!busy.compareAndSet(false, true)) {
            return false;
        }

        Status s = new Status();
        s.state = State.UPLOADING;
        s.fileName = tempFile.getName();
        current = s;

        executor.submit(() -> doUpload(tempFile, destPath, uploadType, chunkSizeMb, s));
        return true;
    }

    private void doUpload(File tempFile, String destPath, String uploadType, int chunkSizeMb, Status s) {
        try {
            UploadProgressCallback callback = new UploadProgressCallback() {
                @Override
                public void onLog(String msg) {
                    s.logs.add(msg);
                    Logger.info(UploadManager.class, "[upload] " + msg);
                }
                @Override
                public void onProgress(int cur, int total) {
                    s.currentPart = cur;
                    s.totalParts = total;
                }
                @Override
                public void onError(String msg) {
                    s.logs.add("ERROR: " + msg);
                    Logger.error(UploadManager.class, "[upload] " + msg);
                }
                @Override
                public void onComplete(String structFile) {
                    s.logs.add("Structure saved: " + structFile);
                }
            };

            boolean success;

            if (uploadType.equalsIgnoreCase("BOT")) {
                int chunkBytes = chunkSizeMb * DiscordCloudCore.MB_SCALAR;
                UploadBotTask task = new UploadBotTask(
                        tempFile,
                        config.getBotToken(),
                        config.getChannelId(),
                        chunkBytes,
                        callback,
                        config.getFilesDirectory(),
                        destPath);
                success = task.execute();
            } else {
                UploadTask task = new UploadTask(
                        tempFile,
                        config.getWebhook(),
                        DiscordCloudCore.CHUNK_FILE_SIZE,
                        callback,
                        config.getFilesDirectory(),
                        destPath);
                success = task.execute();
            }

            if (success) {
                moveStructureFile(tempFile.getName(), destPath, s);
                tempFile.delete();
                s.state = State.DONE;
                s.logs.add("Upload completed successfully.");
                Logger.info(UploadManager.class, "Upload done: " + tempFile.getName());
            } else {
                tempFile.delete();
                if (s.error == null) s.error = "Upload failed.";
                s.state = State.ERROR;
            }

        } catch (Exception e) {
            Logger.error(UploadManager.class, "Upload exception: " + e.getMessage());
            tempFile.delete();
            s.error = e.getMessage();
            s.state = State.ERROR;
        }
    }

    private void moveStructureFile(String originalFileName, String destPath, Status s) throws IOException {
        File dsclSource = new File(originalFileName + FileHelper.STRUCTURE_EXTENSION);

        // Validate destPath is inside filesDirectory (prevent path traversal)
        File baseDir = new File(config.getFilesDirectory()).getCanonicalFile();
        File destDir;
        if (destPath == null || destPath.trim().isEmpty()) {
            destDir = baseDir;
        } else {
            destDir = new File(config.getFilesDirectory(), destPath).getCanonicalFile();
        }

        if (!destDir.toPath().startsWith(baseDir.toPath())) {
            throw new IOException("Invalid destination path: " + destPath);
        }

        destDir.mkdirs();
        File dsclDest = new File(destDir, originalFileName + FileHelper.STRUCTURE_EXTENSION);

        if (!dsclSource.exists()) {
            throw new IOException("Structure file not found: " + dsclSource.getAbsolutePath());
        }

        Files.move(dsclSource.toPath(), dsclDest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        s.logs.add("File .dscl saved in: " + dsclDest.getPath());
        Logger.info(UploadManager.class, "Moved .dscl to: " + dsclDest.getAbsolutePath());
    }

    public UploadStatusDto getStatusDto() {
        Status s = current;
        UploadStatusDto dto = new UploadStatusDto();
        dto.state = s.state.name().toLowerCase();
        dto.fileName = s.fileName;
        dto.currentPart = s.currentPart;
        dto.totalParts = s.totalParts;
        dto.logs = new ArrayList<>(s.logs);
        dto.error = s.error;
        return dto;
    }

    /**
     * Reset to IDLE state. Only possible when state is DONE or ERROR.
     * @return true if reset was successful
     */
    public synchronized boolean reset() {
        Status s = current;
        if (s.state == State.DONE || s.state == State.ERROR) {
            current = new Status();
            busy.set(false);
            return true;
        }
        return false;
    }

    public boolean isBusy() {
        return busy.get();
    }

    public boolean hasWebhook() {
        String w = config.getWebhook();
        return w != null && !w.isBlank() && !w.equals("your_webhook");
    }

    public boolean hasBot() {
        String token = config.getBotToken();
        String channel = config.getChannelId();
        return token != null && !token.isBlank() && channel != null && !channel.isBlank();
    }
}

