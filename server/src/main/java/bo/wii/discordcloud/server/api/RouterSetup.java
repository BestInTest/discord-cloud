package bo.wii.discordcloud.server.api;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.server.auth.LoginRateLimiter;
import bo.wii.discordcloud.server.auth.SessionManager;
import bo.wii.discordcloud.server.auth.TokenManager;
import bo.wii.discordcloud.server.cache.ChunkCache;
import bo.wii.discordcloud.server.config.ServerConfig;
import bo.wii.discordcloud.server.upload.UploadManager;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.io.InputStream;

public class RouterSetup {

    private final ServerConfig config;
    private final AuthController authController;
    private final FilesController filesController;
    private final FileStreamController fileStreamController;
    private final UploadController uploadController;
    private final FolderController folderController;

    public RouterSetup(ServerConfig config, TokenManager tokenManager,
                       SessionManager sessionManager, LoginRateLimiter rateLimiter,
                       ChunkCache chunkCache, UploadManager uploadManager) {
        this.config = config;
        this.authController = new AuthController(config, tokenManager, sessionManager, rateLimiter);
        this.filesController = new FilesController(config, tokenManager, sessionManager);
        this.fileStreamController = new FileStreamController(config, tokenManager, sessionManager, chunkCache);
        this.uploadController = new UploadController(config, tokenManager, sessionManager, uploadManager);
        this.folderController = new FolderController(config, tokenManager, sessionManager);
    }

    public void registerRoutes(JavalinDefaultRoutingApi routes) {
        routes.before(ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("X-Frame-Options", "DENY");
        });

        // Log every incoming request
        routes.before(ctx -> {
            if (!shouldSkipLogging(ctx)) { // ignore upload status endpoint because too much spam in console
                Logger.info(RouterSetup.class,
                        "Request | " + ctx.method() + " " + ctx.path()
                                + " | IP: " + ctx.ip()
                                + " | UA: " + shortUserAgent(ctx.userAgent()));
            }
        });

        routes.post("/api/auth", authController::handleLogin);
        routes.post("/api/logout", authController::handleLogout);
        routes.get("/api/files", filesController::handleListFiles);
        routes.get("/file/{filename}", fileStreamController::handleFileStream);
        routes.get("/api/upload/methods", uploadController::handleMethods);
        routes.get("/api/upload/status", uploadController::handleStatus);
        routes.post("/api/upload/reset", uploadController::handleReset);
        routes.post("/api/upload", uploadController::handleUpload);
        routes.post("/api/folder/create", folderController::handleCreate);
        routes.post("/api/folder/rename", folderController::handleRename);

        // static resources
        routes.get("/", ctx -> resource(ctx, "/index.html", "text/html"));
        routes.get("/app.css", ctx -> resource(ctx, "/app.css", "text/css"));
        routes.get("/app.js", ctx -> resource(ctx, "/app.js", "application/javascript"));
        routes.get("/icons/{filename}", ctx -> {
            String filename = ctx.pathParam("filename");
            resource(ctx, "/icons/" + filename, "image/svg+xml");
        });


        // error handler for unhandled exceptions
        routes.exception(Exception.class, (e, ctx) -> {
            Logger.error(RouterSetup.class, "Unhandled exception for " + ctx.method() + " " + ctx.path() + ": " + e.getMessage());
            ctx.status(500).result("Internal server error");
        });
    }

    private static String shortUserAgent(String ua) {
        if (ua == null) return "Unknown";
        return ua.length() > 80 ? ua.substring(0, 80) + "..." : ua;
    }

    private static void resource(Context ctx, String resource, String contentType) {
        try (InputStream is = RouterSetup.class.getResourceAsStream(resource)) {
            if (is == null) {
                ctx.status(404).result("Not found");
                return;
            }
            ctx.contentType(contentType).result(is.readAllBytes());
        } catch (Exception e) {
            Logger.error(RouterSetup.class, "Failed to serve resource " + resource + ": " + e.getMessage());
            ctx.status(500).result("Internal server error");
        }
    }

    private static boolean shouldSkipLogging(Context ctx) {
        return ctx.method().name().equals("GET") && ctx.path().equals("/api/upload/status");
    }
}
