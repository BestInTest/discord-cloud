package bo.wii.discordcloud.server.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages browser sessions created after successful /api/auth login.
 * Sessions are not saved and are lost on server restart.
 */
public class SessionManager {

    private static final int SESSION_TOKEN_BYTES = 32;

    // Key = sessionToken
    // Val = expiry timestamp (ms)
    private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();

    /**
     * Creates a new session with the given duration and returns the session token.
     * Also cleans expired sessions.
     */
    public String createSession(int durationSeconds) {
        byte[] bytes = new byte[SESSION_TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        long expiresAt = System.currentTimeMillis() + (long) durationSeconds * 1000;
        sessions.put(token, expiresAt);

        cleanupExpiredSessions();
        return token;
    }

    /**
     * Returns true if the session token exists and has not expired.
     */
    public boolean isValidSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isEmpty()) return false;
        Long expiresAt = sessions.get(sessionToken);
        if (expiresAt == null) return false;

        if (System.currentTimeMillis() > expiresAt) {
            sessions.remove(sessionToken);
            return false;
        }
        return true;
    }

    /**
     * Logout
     */
    public void invalidateSession(String sessionToken) {
        if (sessionToken != null) {
            sessions.remove(sessionToken);
        }
    }

    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(stringLongEntry -> stringLongEntry.getValue() < now);
    }
}

