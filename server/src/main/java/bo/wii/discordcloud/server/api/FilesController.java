package bo.wii.discordcloud.server.api;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.server.api.dto.FileInfoDto;
import bo.wii.discordcloud.server.auth.SessionManager;
import bo.wii.discordcloud.server.auth.TokenManager;
import bo.wii.discordcloud.server.config.ServerConfig;
import com.google.gson.Gson;
import io.javalin.http.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class FilesController {

    private static final Gson GSON = new Gson();

    private final ServerConfig config;
    private final TokenManager tokenManager;
    private final SessionManager sessionManager;

    public FilesController(ServerConfig config, TokenManager tokenManager,
                           SessionManager sessionManager) {
        this.config = config;
        this.tokenManager = tokenManager;
        this.sessionManager = sessionManager;
    }

    /**
     * GET /api/files?path=optional/sub/path
     */
    public void handleListFiles(Context ctx) {
        if (!ApiUtil.isAuthorized(ctx, config, tokenManager, sessionManager)) {
            ctx.status(401).contentType("application/json")
                    .result(ApiUtil.jsonError("Unauthorized"));
            return;
        }

        String path = ctx.queryParam("path");
        if (path == null) path = "";

        Logger.info(FilesController.class, "List files | IP: " + ctx.ip()
                + " | Path: " + (path.isEmpty() ? "/" : path));

        // Prevent path traversal via canonical path resolution
        File baseDir;
        File requestedDir;
        try {
            baseDir = new File(config.getFilesDirectory()).getCanonicalFile();
            requestedDir = new File(config.getFilesDirectory(), path).getCanonicalFile();
        } catch (IOException e) {
            Logger.error(FilesController.class, "Failed to resolve path: " + e.getMessage());
            ctx.status(500).contentType("application/json")
                    .result(ApiUtil.jsonError("Server error"));
            return;
        }

        if (!requestedDir.toPath().startsWith(baseDir.toPath())) {
            ctx.status(400).contentType("application/json")
                    .result(ApiUtil.jsonError("Invalid path"));
            return;
        }

        List<FileInfoDto> entries = new ArrayList<>();

        if (!requestedDir.exists() || !requestedDir.isDirectory()) {
            ctx.contentType("application/json").result(GSON.toJson(entries));
            return;
        }

        final String finalPath = path;
        try (Stream<Path> paths = Files.list(requestedDir.toPath())) {
            paths.forEach(p -> {
                File file = p.toFile();
                String entryPath = finalPath.isEmpty()
                        ? file.getName()
                        : finalPath + "/" + file.getName();

                if (file.isDirectory()) {
                    FileInfoDto dto = new FileInfoDto();
                    dto.name = file.getName();
                    dto.isDirectory = true;
                    dto.path = entryPath;
                    entries.add(dto);

                } else if (file.getName().endsWith(FileHelper.STRUCTURE_EXTENSION)
                        || file.getName().endsWith(FileHelper.LEGACY_STRUCTURE_EXTENSION)) {
                    try {
                        FileStruct structure = FileHelper.loadStructureFile(file);
                        if (structure != null && structure.isValid()) {
                            FileInfoDto dto = new FileInfoDto();
                            dto.name = structure.getOriginalFileName();
                            dto.size = structure.getFileSize();
                            dto.parts = structure.getParts().size();
                            dto.uploadTimestamp = structure.getUploadTimestamp();
                            dto.thumbnail = structure.getThumbnailBase64();
                            dto.isDirectory = false;
                            dto.path = entryPath;
                            entries.add(dto);
                        } else {
                            Logger.warn(FilesController.class, "Invalid or corrupted structure: " + file.getName());
                        }
                    } catch (IOException e) {
                        Logger.error(FilesController.class, "Failed to load structure: " + file.getName()
                                + " - " + e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            Logger.error(FilesController.class, "Failed to list directory: " + e.getMessage());
        }

        ctx.contentType("application/json").result(GSON.toJson(entries));
    }
}
