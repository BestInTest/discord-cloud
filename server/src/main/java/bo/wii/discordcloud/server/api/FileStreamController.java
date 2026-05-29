package bo.wii.discordcloud.server.api;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.client.LinkRefresher;
import bo.wii.discordcloud.core.client.LinkRefresherFactory;
import bo.wii.discordcloud.core.services.download.DownloaderService;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.server.auth.SessionManager;
import bo.wii.discordcloud.server.auth.TokenManager;
import bo.wii.discordcloud.server.config.ServerConfig;
import io.javalin.http.Context;

import java.io.File;
import java.io.IOException;

/**
 * Handles GET /file/{filename}
 */
public class FileStreamController {

    private final ServerConfig config;
    private final TokenManager tokenManager;
    private final SessionManager sessionManager;

    public FileStreamController(ServerConfig config, TokenManager tokenManager,
                                SessionManager sessionManager) {
        this.config = config;
        this.tokenManager = tokenManager;
        this.sessionManager = sessionManager;
    }

    /**
     * GET /file/{filename}?path=optional/sub/path
     * <p>
     * Supports standard HTTP Range requests for partial content (seeking, resuming).
     */
    public void handleFileStream(Context ctx) {
        if (!ApiUtil.isAuthorized(ctx, config, tokenManager, sessionManager)) {
            ctx.status(401).result("Unauthorized");
            return;
        }

        String filename = ctx.pathParam("filename");
        String pathParam = ctx.queryParam("path");
        String clientIp = ctx.ip();
        String rangeHeader = ctx.header("Range");

        // Reject path separators in the filename segment to prevent traversal via url params
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            ctx.status(400).result("Invalid filename");
            return;
        }

        // Build full path and prevent directory traversal
        String relativePath = config.getFilesDirectory() + "/";
        if (pathParam != null && !pathParam.isBlank()) {
            relativePath += pathParam + "/";
        }
        relativePath += filename;

        File baseDir;
        File requestedFile;
        try {
            baseDir = new File(config.getFilesDirectory()).getCanonicalFile();
            requestedFile = new File(relativePath).getCanonicalFile();
        } catch (IOException e) {
            Logger.error(FileStreamController.class, "Path resolution failed: " + e.getMessage());
            ctx.status(500).result("Server error");
            return;
        }

        if (!requestedFile.toPath().startsWith(baseDir.toPath())) {
            ctx.status(400).result("Invalid path");
            return;
        }

        // Load file structure
        FileStruct structure;
        try {
            structure = FileHelper.loadStructureFile(requestedFile);
        } catch (IOException e) {
            Logger.error(FileStreamController.class, "Failed to load structure for " + filename
                    + ": " + e.getMessage());
            ctx.status(500).result("Error loading file structure");
            return;
        }

        if (structure == null || !structure.isValid()) {
            ctx.status(404).result("File not found");
            return;
        }

        long fileSize = structure.getFileSize();
        long partSize = structure.getSinglePartSize();
        long start = 0;
        long end = fileSize - 1;

        // Parse Range header if present
        if (rangeHeader != null) {
            String[] parts = rangeHeader.replace("bytes=", "").split("-");
            try {
                start = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    end = Long.parseLong(parts[1]);
                }
            } catch (NumberFormatException e) {
                ctx.status(416).header("Content-Range", "bytes */" + fileSize)
                        .result("Invalid range");
                return;
            }

            if (start < 0 || end >= fileSize || start > end) {
                ctx.status(416).header("Content-Range", "bytes */" + fileSize)
                        .result("Range Not Satisfiable");
                return;
            }

            ctx.status(206).header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
        }

        long contentLength = end - start + 1;

        Logger.info(FileStreamController.class, "File request | IP: " + clientIp
                + " | File: " + filename
                + (rangeHeader != null ? " | Range: " + start + "-" + end : " | Full")
                + " | Size: " + contentLength + " bytes");

        ctx.header("Accept-Ranges", "bytes");
        ctx.header("Content-Length", String.valueOf(contentLength));
        ctx.contentType(ApiUtil.getContentType(structure.getOriginalFileName()));

        // Determine which parts of the file we need
        int startPartIndex = (int) (start / partSize);
        int endPartIndex = (int) (end / partSize);
        long bytesToSkip = start % partSize;

        // Create link refresher based on upload method
        LinkRefresher refresher;
        try {
            refresher = LinkRefresherFactory.createRefresher(structure, config.getWebhook(), config.getBotToken());
        } catch (IllegalArgumentException e) {
            Logger.error(FileStreamController.class, "Cannot create link refresher for " + filename + ": " + e.getMessage());
            ctx.status(500).result("Server configuration error: " + e.getMessage());
            return;
        }

        DownloaderService downloader = new DownloaderService(structure, refresher, config.isPrefetchEnabled());

        MultiPartInputStream stream = new MultiPartInputStream(
                downloader, structure,
                startPartIndex, endPartIndex, bytesToSkip, contentLength,
                config.getCacheDirectory(), config.isAutoRemoveDownloadedFiles());

        // Javalin reads the stream and writes it to the response
        ctx.result(stream);
    }
}
