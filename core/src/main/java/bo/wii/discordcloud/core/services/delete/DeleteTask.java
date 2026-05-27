package bo.wii.discordcloud.core.services.delete;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.structure.ChunkFileInfo;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.structure.enums.UploadType;
import bo.wii.discordcloud.core.utils.ApiUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * Task for deleting all Discord messages associated with a structure file.
 * Supports both webhook-uploaded and bot-uploaded files
 */
public class DeleteTask {

    private final FileStruct structure;
    private final String webhookUrl;
    private final String botToken;
    private final DeleteProgressCallback callback;
    private volatile boolean stopped = false;

    /**
     * @param structure loaded structure file (.json/.dscl)
     * @param webhookUrl webhook URL (required for files uploaded via webhook)
     * @param botToken bot token (required for files uploaded via bot)
     * @param callback progress callback
     */
    public DeleteTask(FileStruct structure, String webhookUrl, String botToken, DeleteProgressCallback callback) {
        this.structure = structure;
        this.webhookUrl = webhookUrl;
        this.botToken = botToken;
        this.callback = callback;
    }

    /**
     * Execute delete task
     * @return true if all parts were deleted successfully, false otherwise
     */
    public boolean execute() {
        UploadType uploadType = structure.getUploadType() != null ? structure.getUploadType() : UploadType.WEBHOOK;

        // Validate webhook channel before starting deletion
        if (uploadType == UploadType.WEBHOOK) {
            //TODO: sprawdzić czy nie występuje problem z użyciem bota który nie ma dostępu do kanału
            String validationError = ApiUtil.validateWebhookChannel(structure, webhookUrl);
            if (validationError != null) {
                callback.onError(validationError);
                return false;
            }
        }

        List<ChunkFileInfo> parts = new ArrayList<>(structure.getParts());
        int total = parts.size();
        int deleted = 0;
        int failed = 0;

        callback.onLog("Deleting: " + structure.getOriginalFileName());
        callback.onLog("Upload type: " + uploadType);
        callback.onLog("Total parts: " + total);

        OkHttpClient client = new OkHttpClient();

        for (int i = 0; i < total && !stopped; i++) {
            if (Thread.currentThread().isInterrupted()) {
                callback.onLog("Deletion interrupted.");
                return false;
            }

            ChunkFileInfo part = parts.get(i);
            String messageId = part.getMessageId();
            int currentPart = i + 1;

            if (messageId == null || messageId.isEmpty()) {
                callback.onLog("Part " + currentPart + "/" + total + " - no message ID, skipping");
                failed++;
                continue;
            }

            callback.onLog("Deleting part " + currentPart + "/" + total);

            boolean success = deletePart(client, uploadType, messageId);

            if (success) {
                deleted++;
                callback.onProgress(deleted, total);
                callback.onLog("Part " + currentPart + "/" + total + " deleted successfully");
            } else {
                failed++;
                callback.onLog("Failed to delete part " + currentPart + "/" + total);
            }

            // Small delay to avoid hitting Discord rate limits
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.onLog("Deletion interrupted.");
                return false;
            }
        }

        if (stopped) {
            callback.onLog("Deletion cancelled.");
            return false;
        }

        callback.onComplete(deleted, total);
        return failed == 0;
    }

    private boolean deletePart(OkHttpClient client, UploadType uploadType, String messageId) {
        String url;
        Request.Builder requestBuilder = new Request.Builder();

        if (uploadType == UploadType.BOT) {
            String channelId = structure.getEffectiveChannelId();
            if (channelId == null || channelId.isEmpty()) {
                Logger.error(DeleteTask.class, "Channel ID is missing, cannot delete via bot");
                return false;
            }
            url = "https://discord.com/api/v9/channels/" + channelId + "/messages/" + messageId;
            requestBuilder.addHeader("Authorization", "Bot " + botToken);
        } else {
            // Webhook mode
            if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("your_webhook")) {
                Logger.error(DeleteTask.class, "Webhook URL is not configured");
                return false;
            }
            url = webhookUrl + "/messages/" + messageId;
        }

        Request request = requestBuilder
                .url(url)
                .delete()
                .build();

        for (int attempts = 0; attempts < 5; attempts++) {
            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                Logger.debug(DeleteTask.class, "DELETE " + url + " -> " + code);

                if (code == 204) {
                    // 204 No Content (deleted successfully)
                    return true;
                }
                if (code == 404) {
                    // Message not found. Treat as success because it was already deleted or never existed
                    Logger.info(DeleteTask.class, "Message " + messageId + " not found (404) - already deleted?");
                    return true;
                }
                if (code == 429) {
                    // Rate limited
                    Logger.info(DeleteTask.class, "Rate limited (429), waiting 5 s...");
                    Thread.sleep(5000);
                    continue;
                }
                if (code == 502) {
                    // Gateway unavailable
                    Thread.sleep(3000);
                    continue;
                }
                if (code == 401) {
                    Logger.error(DeleteTask.class, "Unauthorized (401): invalid bot token or webhook URL");
                    return false;
                }
                if (code == 403) {
                    Logger.error(DeleteTask.class, "Forbidden (403): missing MANAGE_MESSAGES permission");
                    return false;
                }
                Logger.error(DeleteTask.class, "Unexpected response code: " + code);
                return false;

            } catch (SocketTimeoutException e) {
                Logger.error(DeleteTask.class, "SocketTimeoutException, retrying...");
            } catch (IOException e) {
                Logger.error(DeleteTask.class, "IOException: " + e.getMessage());
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Stop the delete task after the current part.
     */
    public void stop() {
        stopped = true;
    }
}

