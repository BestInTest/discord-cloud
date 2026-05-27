package bo.wii.discordcloud.core.client;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.structure.DiscordResponse;
import bo.wii.discordcloud.core.utils.ApiUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Link refresher using Discord bot token
 * Works for any messages in channels the bot has access to
 */
public class BotTokenLinkRefresher implements LinkRefresher {

    private final String botToken;
    private final String channelId;
    private final OkHttpClient client;

    /**
     * Refresher without channelId. ChannelId will be required as a parameter in the refreshLinks()
     */
    public BotTokenLinkRefresher(String botToken) {
        this(botToken, null);
    }

    /**
     * Constructor with optional channelId. If channelId is provided, it will be used to refresh links.
     * If not, it will be required as a parameter in the refreshLinks().
     */
    public BotTokenLinkRefresher(String botToken, String channelId) {
        this.botToken = botToken;
        this.channelId = channelId;
        this.client = new OkHttpClient();
    }

    /**
     * Refreshes file attachment links contained in a message using the bot token.
     * @param messageId ID of the message whose attachments should be refreshed
     * @param channelId ID of the channel containing the message (NOTE: if channelId is provided in the constructor, this parameter will be ignored)
     * @return {@link DiscordResponse} containing refreshed message data, or null on error
     */
    @Override
    public DiscordResponse refreshLinks(long messageId, String channelId) {
        // Use channelId from constructor, parameter as fallback
        String effectiveChannelId = (this.channelId != null && !this.channelId.isEmpty()) ? this.channelId : channelId;
        if (effectiveChannelId == null || effectiveChannelId.isEmpty()) {
            Logger.error(this.getClass(), "Channel ID is required for bot token refresh");
            return null;
        }

        // Discord API endpoint for getting a message
        String url = "https://discord.com/api/v9/channels/" + effectiveChannelId + "/messages/" + messageId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bot " + botToken)
                .build();

        int responseCode = -1;
        boolean success = false;

        // Retry up to 5 times until success
        for (int attempts = 0; (attempts < 5) && (!success); attempts++) {
            try (Response response = client.newCall(request).execute()) {
                responseCode = response.code();
                Logger.debug(this.getClass(), "response: " + responseCode);

                // Too many requests
                if (responseCode == 429) {
                    Thread.sleep(5000);
                    continue;
                }
                
                // Gateway unavailable
                if (responseCode == 502) {
                    Thread.sleep(3000);
                    continue;
                }
                
                // Unauthorized
                if (responseCode == 401) {
                    Logger.error(this.getClass(), "Unauthorized (401): Invalid bot token");
                    if (response.body() != null) {
                        String errorBody = response.body().string();
                        Logger.error(this.getClass(), "Error details: " + errorBody);
                    }
                    return null;
                }

                // Forbidden
                if (responseCode == 403) {
                    Logger.error(this.getClass(), "Forbidden (403): Bot doesn't have permission to read message history in this channel");
                    Logger.error(this.getClass(), "Required permissions: VIEW_CHANNEL, READ_MESSAGE_HISTORY");
                    Logger.error(this.getClass(), "Channel ID: " + effectiveChannelId);
                    if (response.body() != null) {
                        String errorBody = response.body().string();
                        Logger.error(this.getClass(), "Error details: " + errorBody);
                    }
                    return null;
                }
                
                if (responseCode == 404) {
                    Logger.error(this.getClass(), "Not Found (404): Message or channel not found");
                    return ApiUtil.parseResponse(response);
                }
                
                // Success
                if (responseCode == 200) {
                    DiscordResponse discordResponse = ApiUtil.parseResponse(response);
                    if (discordResponse != null && discordResponse.getAttachments() != null && !discordResponse.getAttachments().isEmpty()) {
                        Logger.debug(this.getClass(), "url: " + discordResponse.getAttachments().get(0).getUrl() + "\n");
                    }
                    return discordResponse;
                }

                // Other errors
                Logger.error(this.getClass(), "Unexpected response code: " + responseCode);
                if (response.body() != null) {
                    String errorBody = response.body().string();
                    Logger.error(this.getClass(), "Response body: " + errorBody);
                }
                return null;

            } catch (SocketTimeoutException e) {
                Logger.error(this.getClass(), "SocketTimeoutException: " + e.getMessage() + ". Retrying...");
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
    
    @Override
    public RefresherType getType() {
        return RefresherType.BOT;
    }
}
