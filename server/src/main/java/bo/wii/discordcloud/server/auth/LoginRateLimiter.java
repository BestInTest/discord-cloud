package bo.wii.discordcloud.server.auth;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection for the /api/auth.
 * Tracks failed login attempts per IP
 */
public class LoginRateLimiter {

    private final int maxFailures;
    private final long windowMs;

    // Key = failureCount
    // Val = windowStartMs
    private final ConcurrentHashMap<String, long[]> records = new ConcurrentHashMap<>();

    public LoginRateLimiter(int maxFailures, int windowSeconds) {
        this.maxFailures = maxFailures;
        this.windowMs = (long) windowSeconds * 1000;
    }

    /**
     * Returns true if the given IP has exceeded allowed failure count within the current time window.
     */
    public boolean isBlocked(String ip) {
        long[] record = records.get(ip);
        if (record == null) return false;

        // Expire the window if enough time has passed
        if (System.currentTimeMillis() - record[1] > windowMs) {
            records.remove(ip);
            return false;
        }
        return record[0] >= maxFailures;
    }

    /**
     * Records a failed login attempt from the given ip
     */
    public void recordFailure(String ip) {
        records.compute(ip, (k, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing[1] > windowMs) {
                // Start a new window
                return new long[]{1, now};
            }
            existing[0]++;
            return existing;
        });
    }

    /**
     * Resets the failure counter for the given ip (e.g. after a successful login)
     */
    public void reset(String ip) {
        records.remove(ip);
    }
}

