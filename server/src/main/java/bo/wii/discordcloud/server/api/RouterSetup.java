package bo.wii.discordcloud.server.api;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.server.auth.LoginRateLimiter;
import bo.wii.discordcloud.server.auth.SessionManager;
import bo.wii.discordcloud.server.auth.TokenManager;
import bo.wii.discordcloud.server.config.ServerConfig;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.InputStream;

public class RouterSetup {

    private final ServerConfig config;
    private final AuthController authController;
    private final FilesController filesController;
    private final FileStreamController fileStreamController;

    public RouterSetup(ServerConfig config, TokenManager tokenManager,
                       SessionManager sessionManager, LoginRateLimiter rateLimiter) {
        this.config = config;
        this.authController = new AuthController(config, tokenManager, sessionManager, rateLimiter);
        this.filesController = new FilesController(config, tokenManager, sessionManager);
        this.fileStreamController = new FileStreamController(config, tokenManager, sessionManager);
    }

    public void registerRoutes(Javalin app) {
        app.before(ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("X-Frame-Options", "DENY");
        });

        // Log every incoming request
        app.before(ctx -> Logger.info(RouterSetup.class,
                "Request | " + ctx.method() + " " + ctx.path()
                        + " | IP: " + ctx.ip()
                        + " | UA: " + shortUserAgent(ctx.userAgent())));

        // Web UI static resources
        app.get("/", ctx -> resource(ctx, "/index.html", "text/html"));
        app.get("/app.css", ctx -> resource(ctx, "/app.css", "text/css"));
        app.get("/app.js", ctx -> resource(ctx, "/app.js", "application/javascript"));

        // Authentication
        app.post("/api/auth",   authController::handleLogin);
        app.post("/api/logout", authController::handleLogout);

        // File listing
        app.get("/api/files", filesController::handleListFiles);

        // File streaming
        app.get("/file/{filename}", fileStreamController::handleFileStream);

        // error handler for unhandled exceptions
        app.exception(Exception.class, (e, ctx) -> {
            Logger.error(RouterSetup.class, "Unhandled exception for "
                    + ctx.method() + " " + ctx.path() + ": " + e.getMessage());
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
}
