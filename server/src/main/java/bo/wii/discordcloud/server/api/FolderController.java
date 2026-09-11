package bo.wii.discordcloud.server.api;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.server.auth.SessionManager;
import bo.wii.discordcloud.server.auth.TokenManager;
import bo.wii.discordcloud.server.config.ServerConfig;
import com.google.gson.Gson;
import io.javalin.http.Context;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class FolderController {

    private static final Gson GSON = new Gson();

    private final ServerConfig config;
    private final TokenManager tokenManager;
    private final SessionManager sessionManager;

    public FolderController(ServerConfig config, TokenManager tokenManager, SessionManager sessionManager) {
        this.config = config;
        this.tokenManager = tokenManager;
        this.sessionManager = sessionManager;
    }

    /**
     * POST /api/folder/create
     */
    public void handleCreate(Context ctx) {
        if (!ApiUtil.isAuthorized(ctx, config, tokenManager, sessionManager)) {
            ctx.status(401).contentType("application/json").result(ApiUtil.jsonError("Unauthorized"));
            return;
        }

        Map<?, ?> body = GSON.fromJson(ctx.body(), Map.class);
        if (body == null) {
            ctx.status(400).contentType("application/json").result(ApiUtil.jsonError("Invalid request body"));
            return;
        }

        String parentPath = body.get("path") != null ? (String) body.get("path") : "";
        String name = body.get("name") != null ? ((String) body.get("name")).trim() : "";

        String validationError = validateFolderName(name);
        if (validationError != null) {
            ctx.status(400).contentType("application/json").result(ApiUtil.jsonError(validationError));
            return;
        }

        File baseDir;
        File targetDir;
        try {
            baseDir = new File(config.getFilesDirectory()).getCanonicalFile();
            String relative = parentPath.isEmpty() ? name : parentPath + "/" + name;
            targetDir = new File(config.getFilesDirectory(), relative).getCanonicalFile();
        } catch (IOException e) {
            Logger.error(FolderController.class, "Failed to resolve path: " + e.getMessage());
            ctx.status(500).contentType("application/json").result(ApiUtil.jsonError("Server error"));
            return;
        }

        if (!targetDir.toPath().startsWith(baseDir.toPath())) {
            ctx.status(400).contentType("application/json").result(ApiUtil.jsonError("Invalid path"));
            return;
        }

        if (targetDir.exists()) {
            ctx.status(409).contentType("application/json").result(ApiUtil.jsonError("Folder already exists"));
            return;
        }

        if (!targetDir.mkdirs()) {
            ctx.status(500).contentType("application/json").result(ApiUtil.jsonError("Failed to create folder"));
            return;
        }

        Logger.info(FolderController.class, "Created folder: " + targetDir.getPath() + " | IP: " + ctx.ip());

        ctx.contentType("application/json").result(ApiUtil.jsonSuccess());
    }

    /**
     * POST /api/folder/rename
     */
    public void handleRename(Context ctx) {
        if (!ApiUtil.isAuthorized(ctx, config, tokenManager, sessionManager)) {
            ctx.status(401).contentType("application/json").result(ApiUtil.jsonError("Unauthorized"));
            return;
        }

        Map<?, ?> body = GSON.fromJson(ctx.body(), Map.class);
        if (body == null) {
            ctx.status(400).contentType("application/json").result(ApiUtil.jsonError("Invalid request body"));
            return;
        }

        String folderPath = body.get("path") != null ? ((String) body.get("path")).trim() : "";
        String newName = body.get("newName") != null ? ((String) body.get("newName")).trim() : "";

        if (folderPath.isEmpty()) {
            ctx.status(400).contentType("application/json").result(ApiUtil.jsonError("Path is required"));
            return;
        }

        String validationError = validateFolderName(newName);
        if (validationError != null) {
            ctx.status(400).contentType("application/json").result(ApiUtil.jsonError(validationError));
            return;
        }

        File baseDir;
        File sourceDir;
        File targetDir;
        try {
            baseDir = new File(config.getFilesDirectory()).getCanonicalFile();
            sourceDir = new File(config.getFilesDirectory(), folderPath).getCanonicalFile();
            targetDir = new File(sourceDir.getParentFile(), newName).getCanonicalFile();
        } catch (IOException e) {
            Logger.error(FolderController.class, "Failed to resolve path: " + e.getMessage());
            ctx.status(500).contentType("application/json").result(ApiUtil.jsonError("Server error"));
            return;
        }

        if (!sourceDir.toPath().startsWith(baseDir.toPath())
                || !targetDir.toPath().startsWith(baseDir.toPath())) {
            ctx.status(400).contentType("application/json").result(ApiUtil.jsonError("Invalid path"));
            return;
        }

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            ctx.status(404).contentType("application/json").result(ApiUtil.jsonError("Folder not found"));
            return;
        }

        if (targetDir.exists()) {
            ctx.status(409).contentType("application/json").result(ApiUtil.jsonError("A folder with that name already exists"));
            return;
        }

        if (!sourceDir.renameTo(targetDir)) {
            ctx.status(500).contentType("application/json").result(ApiUtil.jsonError("Failed to rename folder"));
            return;
        }

        Logger.info(FolderController.class, "Renamed folder: " + sourceDir.getName() + " -> " + targetDir.getName() + " | IP: " + ctx.ip());

        ctx.contentType("application/json").result(ApiUtil.jsonSuccess());
    }

    //TODO: lepsza walidacja?
    private static String validateFolderName(String name) {
        if (name == null || name.isEmpty()) {
            return "Folder name is required";
        }
        if (name.contains("/") || name.contains("\\")) {
            return "Folder name cannot contain slashes";
        }
        if (name.equals(".") || name.equals("..")) {
            return "Invalid folder name";
        }
        if (name.length() > 255) {
            return "Folder name is too long";
        }
        if (name.matches(".*[<>:\"|?*\u0000].*")) {
            return "Folder name contains invalid characters";
        }
        return null;
    }
}

