package bo.wii.discordcloud.server.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class TokenManager {

    // Number of random bytes for token generation
    private static final int TOKEN_BYTES = 32; // 256 bits

    private final File tokenFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // SHA-256 hashes (as byte arrays) of valid tokens
    private final List<byte[]> tokenHashes = new ArrayList<>();

    public TokenManager(String tokenFilePath) {
        this.tokenFile = new File(tokenFilePath);
        loadTokens();
    }

    /**
     * Generates a new random access token, stores its hash, and returns the raw value.
     * The raw value is shown only once and cannot be recovered
     */
    public String generateAndStoreToken() throws IOException {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        byte[] hash = sha256(rawToken);
        synchronized (tokenHashes) {
            tokenHashes.add(hash);
            saveTokens();
        }

        return rawToken;
    }

    /**
     * Removes a token by its raw value.
     * @return true if the token was found and removed, false otherwise.
     */
    public boolean removeToken(String rawToken) throws IOException {
        if (rawToken == null || rawToken.isEmpty()) return false;

        byte[] targetHash = sha256(rawToken);
        synchronized (tokenHashes) {
            boolean removed = tokenHashes.removeIf(h -> MessageDigest.isEqual(h, targetHash));
            if (removed) {
                saveTokens();
            }
            return removed;
        }
    }

    /**
     * Checks if the given raw token is valid.
     */
    public boolean isValidToken(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) return false;
        byte[] inputHash = sha256(rawToken);

        synchronized (tokenHashes) {
            for (byte[] storedHash : tokenHashes) {
                if (MessageDigest.isEqual(inputHash, storedHash)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getTokenCount() {
        synchronized (tokenHashes) {
            return tokenHashes.size();
        }
    }

    private void saveTokens() throws IOException {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (byte[] hash : tokenHashes) {
            entries.add(Map.of("hash", bytesToHex(hash)));
        }

        Map<String, Object> root = Map.of(
                "_comment", "Access tokens for DiscordCloud Server. DO NOT edit manually.",
                "tokens", entries
        );

        if (!tokenFile.exists()) {
            File parent = tokenFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            if (!tokenFile.createNewFile()) {
                throw new IOException("Failed to create token file: " + tokenFile.getAbsolutePath());
            }
        }

        try (Writer writer = new FileWriter(tokenFile, StandardCharsets.UTF_8)) {
            gson.toJson(root, writer);
        }
    }

    private void loadTokens() {
        if (!tokenFile.exists()) return;

        try (Reader reader = new FileReader(tokenFile, StandardCharsets.UTF_8)) {
            Type rootType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> root = gson.fromJson(reader, rootType);
            if (root == null) return;

            @SuppressWarnings("unchecked")
            List<Map<String, String>> entries = (List<Map<String, String>>) root.get("tokens");
            if (entries == null) return;

            synchronized (tokenHashes) {
                for (Map<String, String> entry : entries) {
                    String hex = entry.get("hash");
                    if (hex != null && !hex.isEmpty()) {
                        tokenHashes.add(hexToBytes(hex));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[TokenManager] Failed to load tokens: " + e.getMessage());
        }
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
