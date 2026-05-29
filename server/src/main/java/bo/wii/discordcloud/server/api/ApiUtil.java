package bo.wii.discordcloud.server.api;

import bo.wii.discordcloud.server.auth.SessionManager;
import bo.wii.discordcloud.server.auth.TokenManager;
import bo.wii.discordcloud.server.config.ServerConfig;
import com.google.gson.Gson;
import io.javalin.http.Context;

import java.util.Map;

public class ApiUtil {

    private static final Gson GSON = new Gson();

    // MIME types by file extension (https://www.iana.org/assignments/media-types/)
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("jpg",  "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png",  "image/png"),
            Map.entry("gif",  "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp",  "image/bmp"),
            Map.entry("ico",  "image/x-icon"),
            Map.entry("svg",  "image/svg+xml"),
            Map.entry("mp3",  "audio/mpeg"),
            Map.entry("wav",  "audio/wav"),
            Map.entry("ogg",  "audio/ogg"),
            Map.entry("mp4",  "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("mkv",  "video/x-matroska"),
            Map.entry("mov",  "video/quicktime"),
            Map.entry("avi",  "video/x-msvideo"),
            Map.entry("pdf",  "application/pdf"),
            Map.entry("zip",  "application/zip"),
            Map.entry("txt",  "text/plain"),
            Map.entry("html", "text/html"),
            Map.entry("css",  "text/css"),
            Map.entry("js",   "application/javascript"),
            Map.entry("json", "application/json"),
            Map.entry("xml",  "application/xml")
    );

    private ApiUtil() {}

    public static String jsonError(String message) {
        return GSON.toJson(Map.of("error", message));
    }

    public static String jsonSuccess() {
        return GSON.toJson(Map.of("success", true));
    }

    public static String jsonSuccess(String message) {
        return GSON.toJson(Map.of("success", true, "message", message));
    }

    /**
     * Returns MIME content type for the given file based on its extension.
     * If extension is unknown or missing, defaults to {@code application/octet-stream}
     */
    public static String getContentType(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "application/octet-stream";
        }
        String ext = filename.substring(dotIndex + 1).toLowerCase();
        return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    public static boolean isAuthorized(Context ctx, ServerConfig config, TokenManager tokenManager, SessionManager sessionManager) {
        if (!config.isRequireToken() || tokenManager == null) return true;

        // Check Bearer token in Authorization header (api clients)
        String authHeader = ctx.header("Authorization");
        if (authHeader != null) {
            String token = authHeader.startsWith("Bearer ")
                    ? authHeader.substring(7).trim()
                    : authHeader.trim();
            if (tokenManager.isValidToken(token)) return true;
        }

        // Check session cookie (browser clients)
        String sessionToken = ctx.cookie("auth_session");
        return sessionManager.isValidSession(sessionToken);
    }
}
