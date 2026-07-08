package bo.wii.discordcloud.server.api;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.server.api.dto.UploadStatusDto;
import bo.wii.discordcloud.server.auth.SessionManager;
import bo.wii.discordcloud.server.auth.TokenManager;
import bo.wii.discordcloud.server.config.ServerConfig;
import bo.wii.discordcloud.server.upload.UploadManager;
import com.google.gson.Gson;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Map;

public class UploadController {

    private static final Gson GSON = new Gson();
    private static final long MAX_FILE_SIZE_BYTES = 8L * 1024 * 1024 * 1024; // TODO: do configu?

    private final ServerConfig config;
    private final TokenManager tokenManager;
    private final SessionManager sessionManager;
    private final UploadManager uploadManager;

    public UploadController(ServerConfig config, TokenManager tokenManager,
                            SessionManager sessionManager, UploadManager uploadManager) {
        this.config = config;
        this.tokenManager = tokenManager;
        this.sessionManager = sessionManager;
        this.uploadManager = uploadManager;
    }

    /**
     * GET /api/upload/methods
     * configured upload methods
     */
    public void handleMethods(Context ctx) {
        if (!ApiUtil.isAuthorized(ctx, config, tokenManager, sessionManager)) {
            ctx.status(401).contentType("application/json").result(ApiUtil.jsonError("Unauthorized"));
            return;
        }
        ctx.contentType("application/json").result(GSON.toJson(
                Map.of("hasWebhook", uploadManager.hasWebhook(),
                        "hasBot", uploadManager.hasBot(),
                        "defaultChunkSizeMb", config.getUploadChunkSizeMb())
        ));
    }

    /**
     * GET /api/upload/status
     * current upload state for polling
     */
    public void handleStatus(Context ctx) {
        if (!ApiUtil.isAuthorized(ctx, config, tokenManager, sessionManager)) {
            ctx.status(401).contentType("application/json").result(ApiUtil.jsonError("Unauthorized"));
            return;
        }
        UploadStatusDto dto = uploadManager.getStatusDto();
        ctx.contentType("application/json").result(GSON.toJson(dto));
    }

    /**
     * POST /api/upload/reset
     * <p>
     * Resets to IDLE after DONE or ERROR state
     */
    public void handleReset(Context ctx) {
        if (!ApiUtil.isAuthorized(ctx, config, tokenManager, sessionManager)) {
            ctx.status(401).contentType("application/json").result(ApiUtil.jsonError("Unauthorized"));
            return;
        }
        boolean ok = uploadManager.reset();
        if (ok) {
            ctx.contentType("application/json").result(ApiUtil.jsonSuccess("Reset successful"));
        } else {
            ctx.status(409).contentType("application/json").result(ApiUtil.jsonError("Upload still in progress"));
        }
    }

    /**
     * POST /api/upload
     */
    public void handleUpload(Context ctx) {
        if (!ApiUtil.isAuthorized(ctx, config, tokenManager, sessionManager)) {
            ctx.status(401).contentType("application/json").result(ApiUtil.jsonError("Unauthorized"));
            return;
        }

        // Check if another upload is already running
        if (uploadManager.isBusy()) {
            ctx.status(409).contentType("application/json").result(ApiUtil.jsonError("Another upload is already in progress. Please wait for it to complete."));
            return;
        }

        // Validate upload type
        String uploadType = ctx.formParam("uploadType");
        if (uploadType == null || (!uploadType.equalsIgnoreCase("WEBHOOK") && !uploadType.equalsIgnoreCase("BOT"))) {
            ctx.status(400).contentType("application/json")
                    .result(ApiUtil.jsonError("Invalid upload type. Use WEBHOOK or BOT."));
            return;
        }
        if (uploadType.equalsIgnoreCase("WEBHOOK") && !uploadManager.hasWebhook()) {
            ctx.status(400).contentType("application/json")
                    .result(ApiUtil.jsonError("Webhook is not configured on the server."));
            return;
        }
        if (uploadType.equalsIgnoreCase("BOT") && !uploadManager.hasBot()) {
            ctx.status(400).contentType("application/json")
                    .result(ApiUtil.jsonError("Bot token or channel ID are not configured on the server."));
            return;
        }

        // Validate destination path
        String destPath = ctx.formParam("path");
        if (destPath == null) destPath = "";

        UploadedFile uploadedFile = ctx.uploadedFile("file");
        if (uploadedFile == null) {
            ctx.status(400).contentType("application/json").result(ApiUtil.jsonError("No file was uploaded."));
            return;
        }

        String rawName = uploadedFile.filename();
        if (rawName.isBlank()) {
            ctx.status(400).contentType("application/json").result(ApiUtil.jsonError("Missing file name."));
            return;
        }
        String fileName = Paths.get(rawName).getFileName().toString();
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_"); // Remove problematic characters

        Logger.info(UploadController.class, "Upload request | file=" + fileName
                + " | dest=" + (destPath.isEmpty() ? "/" : destPath)
                + " | type=" + uploadType
                + " | IP=" + ctx.ip());

        // Save to temp dir
        File uploadTempDir = new File(config.getCacheDirectory(), "upload-temp");
        uploadTempDir.mkdirs();
        File tempFile = new File(uploadTempDir, fileName);

        try (InputStream is = uploadedFile.content();
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[64 * 1024]; // 64 KB
            int read;
            long total = 0;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                total += read;
                if (total > MAX_FILE_SIZE_BYTES) {
                    fos.close();
                    tempFile.delete();
                    ctx.status(413).contentType("application/json").result(ApiUtil.jsonError("File is too large (max " + FileHelper.formatFileSize(MAX_FILE_SIZE_BYTES) + ")"));
                    return;
                }
            }
        } catch (Exception e) {
            Logger.error(UploadController.class, "Failed to save temp file: " + e.getMessage());
            tempFile.delete();
            ctx.status(500).contentType("application/json").result(ApiUtil.jsonError("Failed to save temp file: " + e.getMessage()));
            return;
        }

        // Resolve chunk size (BOT mode only)
        int chunkSizeMb = config.getUploadChunkSizeMb();
        if (uploadType.equalsIgnoreCase("BOT")) {
            String chunkSizeStr = ctx.formParam("chunkSizeMb");
            if (chunkSizeStr != null && !chunkSizeStr.isBlank()) {
                try {
                    int parsed = Integer.parseInt(chunkSizeStr.trim());
                    if (parsed < 1 || parsed > 500) {
                        tempFile.delete();
                        ctx.status(400).contentType("application/json").result(ApiUtil.jsonError("Chunk size must be between 1 and 500 MB."));
                        return;
                    }
                    chunkSizeMb = parsed;
                } catch (NumberFormatException e) {
                    tempFile.delete();
                    ctx.status(400).contentType("application/json").result(ApiUtil.jsonError("Invalid chunk size value."));
                    return;
                }
            }
        }

        // Delete any leftover .dscl from previous interrupted upload for this filename
        //TODO: sprawdzić kontynuację uploadu, jeśli plik już istnieje
        File leftoverDscl = new File(fileName + ".dscl");
        if (leftoverDscl.exists()) {
            leftoverDscl.delete();
            Logger.info(UploadController.class, "Deleted leftover .dscl: " + leftoverDscl.getName());
        }

        boolean started = uploadManager.startUpload(tempFile, destPath, uploadType, chunkSizeMb);
        if (!started) {
            tempFile.delete();
            ctx.status(409).contentType("application/json").result(ApiUtil.jsonError("Another upload is already in progress."));
            return;
        }

        ctx.status(202).contentType("application/json").result(GSON.toJson(Map.of("started", true, "fileName", fileName)));
    }
}

