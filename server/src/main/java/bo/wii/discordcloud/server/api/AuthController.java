package bo.wii.discordcloud.server.api;

import bo.wii.discordcloud.server.auth.LoginRateLimiter;
import bo.wii.discordcloud.server.auth.SessionManager;
import bo.wii.discordcloud.server.auth.TokenManager;
import bo.wii.discordcloud.server.config.ServerConfig;
import com.google.gson.Gson;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;

import java.util.Map;

public class AuthController {

    private static final String SESSION_COOKIE = "auth_session";
    private static final Gson GSON = new Gson();

    private final ServerConfig config;
    private final TokenManager tokenManager;
    private final SessionManager sessionManager;
    private final LoginRateLimiter rateLimiter;

    public AuthController(ServerConfig config, TokenManager tokenManager, SessionManager sessionManager, LoginRateLimiter rateLimiter) {
        this.config = config;
        this.tokenManager = tokenManager;
        this.sessionManager = sessionManager;
        this.rateLimiter = rateLimiter;
    }

    /**
     * POST /api/auth
     */
    public void handleLogin(Context ctx) {
        ctx.contentType("application/json");

        // If auth is disabled, session is not needed
        if (!config.isRequireToken() || tokenManager == null) {
            ctx.result(ApiUtil.jsonSuccess("Auth not required"));
            return;
        }

        String clientIp = ctx.ip();

        if (rateLimiter.isBlocked(clientIp)) {
            ctx.status(429).result(ApiUtil.jsonError("Too many failed attempts. Try again later."));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> body = GSON.fromJson(ctx.body(), Map.class);
        String rawToken = body != null ? body.get("token") : null;

        if (rawToken == null || rawToken.isBlank() || !tokenManager.isValidToken(rawToken.trim())) {
            rateLimiter.recordFailure(clientIp);
            ctx.status(401).result(ApiUtil.jsonError("Invalid token"));
            return;
        }

        // Reset rate limit on success
        rateLimiter.reset(clientIp);

        String sessionToken = sessionManager.createSession(config.getSessionDurationSeconds());
        ctx.cookie(buildSessionCookie(sessionToken, config.getSessionDurationSeconds()));
        ctx.result(ApiUtil.jsonSuccess());
    }

    /**
     * POST /api/logout
     */
    public void handleLogout(Context ctx) {
        ctx.contentType("application/json");

        String sessionToken = ctx.cookie(SESSION_COOKIE);
        if (sessionToken != null) {
            sessionManager.invalidateSession(sessionToken);
        }

        // Clear cookie by setting Max-Age=0
        ctx.cookie(buildSessionCookie("", 0));
        ctx.result(ApiUtil.jsonSuccess());
    }

    private Cookie buildSessionCookie(String value, int maxAge) {
        Cookie cookie = new Cookie(SESSION_COOKIE, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(true);
        cookie.setSameSite(SameSite.LAX);
        cookie.setSecure(config.isSsl());
        return cookie;
    }

}
