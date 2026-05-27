package bo.wii.discordcloud.core.client;

import bo.wii.discordcloud.core.structure.DiscordResponse;

public interface LinkRefresher {

    /**
     * Fetch message data from Discord API to refresh attachment links
     * @param messageId The message ID containing the attachment
     * @param channelId The channel ID where the message was sent (may be null for webhook-based refresh)
     * @return DiscordResponse containing fresh attachment URLs, or null if failed
     */
    DiscordResponse refreshLinks(long messageId, String channelId);

    /**
     * Get the type of this link refresher
     * @return The refresher type
     */
    RefresherType getType();

    enum RefresherType {
        WEBHOOK,
        BOT
    }
}

