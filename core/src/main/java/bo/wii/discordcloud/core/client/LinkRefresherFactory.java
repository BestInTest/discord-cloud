package bo.wii.discordcloud.core.client;

import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.structure.enums.UploadType;

/**
 * Factory for creating appropriate LinkRefresher based on FileStruct metadata
 */
public class LinkRefresherFactory {
    
    /**
     * Create a LinkRefresher based on FileStruct upload type and provided credentials
     * @param structure The file structure containing upload metadata
     * @param webhookUrl Webhook URL (used if file was uploaded via webhook)
     * @param botToken Bot token (used if file was uploaded via bot)
     * @return Appropriate LinkRefresher instance
     * @throws IllegalArgumentException if required credentials are missing
     */
    public static LinkRefresher createRefresher(FileStruct structure, String webhookUrl, String botToken) {
        UploadType uploadType = structure.getUploadType();
        
        // Default to WEBHOOK if not specified
        if (uploadType == null) {
            uploadType = UploadType.WEBHOOK;
        }
        
        switch (uploadType) {
            case WEBHOOK:
                if (webhookUrl == null || webhookUrl.isEmpty()) {
                    throw new IllegalArgumentException("Webhook URL is required for files uploaded via webhook");
                }
                return new WebhookLinkRefresher(webhookUrl);
                
            case BOT:
                if (botToken == null || botToken.isEmpty()) {
                    throw new IllegalArgumentException("Bot token is required for files uploaded via bot");
                }
                String channelId = structure.getEffectiveChannelId();
                if (channelId == null || channelId.isEmpty()) {
                    throw new IllegalArgumentException("Channel ID is missing in structure file and could not be extracted from attachment URL");
                }
                return new BotTokenLinkRefresher(botToken, channelId);

            default:
                throw new IllegalArgumentException("Unknown upload type: " + uploadType);
        }
    }
    
    /**
     * Create a LinkRefresher using webhook
     */
    public static LinkRefresher createWebhookRefresher(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            throw new IllegalArgumentException("Webhook URL cannot be null or empty");
        }
        return new WebhookLinkRefresher(webhookUrl);
    }
    
    /**
     * Create a LinkRefresher using bot token.
     * Note: This method assumes that the channel ID will be provided during refresh.
     */
    public static LinkRefresher createBotTokenRefresher(String botToken) {
        if (botToken == null || botToken.isEmpty()) {
            throw new IllegalArgumentException("Bot token cannot be null or empty");
        }
        return new BotTokenLinkRefresher(botToken);
    }

    /**
     * Create a LinkRefresher using bot token with pre-resolved channel ID
     */
    public static LinkRefresher createBotTokenRefresher(String botToken, String channelId) {
        if (botToken == null || botToken.isEmpty()) {
            throw new IllegalArgumentException("Bot token cannot be null or empty");
        }
        return new BotTokenLinkRefresher(botToken, channelId);
    }
}

