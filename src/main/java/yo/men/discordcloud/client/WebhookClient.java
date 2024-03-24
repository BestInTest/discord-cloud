package yo.men.discordcloud.client;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import yo.men.discordcloud.Logger;
import yo.men.discordcloud.structure.DiscordFilePart;
import yo.men.discordcloud.structure.DiscordFileStruct;
import yo.men.discordcloud.structure.DiscordResponse;
import yo.men.discordcloud.utils.ApiUtil;

import java.io.IOException;
import java.util.LinkedHashSet;

public class WebhookClient {

    private final String WEBHOOK_URL;

    public WebhookClient(String webhookUrl) {
        this.WEBHOOK_URL = webhookUrl;
    }

    public DiscordResponse fetchFromApi(long messageId) {
        Request request = new Request.Builder()
                .url(WEBHOOK_URL + "/messages/" + messageId)
                .build();

        OkHttpClient client = new OkHttpClient();
        /*todo:
           handle http 429
           powiadomienie o sprawdzeniu poprawnosci webhooka w przypadku http 401

           dobrze by było tutaj ale jak nie to tam gdzie jest użyta ta metoda
           btw trzeba ogarnac jej uzycie przy downloadzie i uploadzie
         */
        try (Response response = client.newCall(request).execute()) {
            int responseCode = response.code();
            Logger.log(this.getClass(), "response: " + responseCode);
            DiscordResponse discordResponse = ApiUtil.parseResponse(response);
            Logger.log(this.getClass(), "url: " + discordResponse.getAttachments().get(0).getUrl() + "\n");
            return discordResponse;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
