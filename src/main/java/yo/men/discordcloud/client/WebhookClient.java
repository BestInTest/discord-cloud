package yo.men.discordcloud.client;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import yo.men.discordcloud.Logger;
import yo.men.discordcloud.structure.DiscordResponse;
import yo.men.discordcloud.utils.ApiUtil;

import java.io.IOException;
import java.net.SocketTimeoutException;

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
           handle http 429 (i 502?)
           powiadomienie o sprawdzeniu poprawnosci webhooka w przypadku http 401

           dobrze by było tutaj ale jak nie to tam gdzie jest użyta ta metoda
           btw trzeba ogarnac jej uzycie przy downloadzie i uploadzie
         */
        int responseCode = -1;
        boolean success = false;

        //próbowanie 5 razy aż będzie "success"
        for (int attemps = 0; (attemps < 5) && (!success); attemps++) {
            try (Response response = client.newCall(request).execute()) {
                responseCode = response.code();
                Logger.log(this.getClass(), "response: " + responseCode);

                //too many requests
                if (responseCode == 429) {
                    //czekanie aż zejdzie rate limit i ponawianie próby
                    Thread.sleep(5000); // nie wiem czy 5s to nie za mało
                    continue;
                }

                //Gateway unavailable
                if (responseCode == 502) {
                    /*
                    Według dokumentacji wystarczy poczekać i spróbować ponownie.
                    https://discord.com/developers/docs/topics/opcodes-and-status-codes#http
                    */
                    Thread.sleep(3000);
                    continue;
                }

                if (responseCode == 404) {
                    //todo: zamienić na obsługiwanie kodów discorda (tutaj będzie "message": "Unknown Message", "code": 10008)
                    /*
                    Z powodów struktury tego oto programu postanowiłem parsować
                    odpowiedź w przypadku http 404. Przez to trzeba pamiętać o
                    sprawdzaniu czy zwrócone przez tą metodę dane (id oraz attachments)
                    nie są nullem.
                     */
                    return ApiUtil.parseResponse(response);
                }
                DiscordResponse discordResponse = ApiUtil.parseResponse(response);
                Logger.log(this.getClass(), "url: " + discordResponse.getAttachments().get(0).getUrl() + "\n");
                return discordResponse;
            } catch (SocketTimeoutException e) {
                Logger.err(this.getClass(), "SocketTimeoutException: " + e.getMessage() + ". Retrying...");
                continue;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

}
