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
 * Link refresher using webhook URL
 * Works only for messages sent by the same webhook
 */
public class WebhookLinkRefresher implements LinkRefresher {
    
    private final String webhookUrl;
    private final OkHttpClient client;
    
    public WebhookLinkRefresher(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.client = new OkHttpClient();
    }
    
    @Override
    public DiscordResponse refreshLinks(long messageId, String channelId) {
        Request request = new Request.Builder()
                .url(webhookUrl + "/messages/" + messageId)
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
                
                if (responseCode == 404) {
                    return ApiUtil.parseResponse(response);
                }
                
                DiscordResponse discordResponse = ApiUtil.parseResponse(response);
                if (discordResponse != null && discordResponse.getAttachments() != null && !discordResponse.getAttachments().isEmpty()) {
                    Logger.debug(this.getClass(), "url: " + discordResponse.getAttachments().get(0).getUrl() + "\n");
                }
                return discordResponse;
                
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
        return RefresherType.WEBHOOK;
    }
}

