package bo.wii.discordcloud.core.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * Diagnostic tool for testing bot token and permissions
 */
public class BotTokenDiagnostics {

    private final String botToken;
    private final OkHttpClient client;

    public BotTokenDiagnostics(String botToken) {
        this.botToken = botToken;
        this.client = new OkHttpClient();
    }

    /**
     * Test if bot token is valid
     */
    public boolean testBotToken() {
        String url = "https://discord.com/api/v9/users/@me";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bot " + botToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();

            if (code == 200) {
                if (response.body() != null) {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    String username = json.get("username").getAsString();
                    String id = json.get("id").getAsString();
                    System.out.println("[OK] Bot token is valid");
                    System.out.println("     Bot name: " + username);
                    System.out.println("     Bot ID: " + id);
                    return true;
                }
            } else if (code == 401) {
                System.out.println("[X]  Bot token is INVALID");
                return false;
            } else {
                System.out.println("[X]  Unexpected response: " + code);
                return false;
            }
        } catch (IOException e) {
            System.out.println("[X]  Error testing bot token: " + e.getMessage());
        }

        return false;
    }

    /**
     * Test if bot can access a channel
     */
    public boolean testChannelAccess(String channelId) {
        String url = "https://discord.com/api/v9/channels/" + channelId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bot " + botToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();

            if (code == 200) {
                if (response.body() != null) {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    String name = json.has("name") ? json.get("name").getAsString() : "DM";
                    System.out.println("[OK] Bot can access channel: " + name);
                    return true;
                }
            } else if (code == 403) {
                System.out.println("[X]  Bot CANNOT access channel (403 Forbidden)");
                System.out.println("     The bot needs VIEW_CHANNEL permission");
                if (response.body() != null) {
                    String body = response.body().string();
                    System.out.println("     Error: " + body);
                }
                return false;
            } else if (code == 404) {
                System.out.println("[X]  Channel not found (404)");
                System.out.println("     Check if channel ID is correct: " + channelId);
                return false;
            } else {
                System.out.println("[X]  Unexpected response: " + code);
                if (response.body() != null) {
                    String body = response.body().string();
                    System.out.println("     Response: " + body);
                }
                return false;
            }
        } catch (IOException e) {
            System.out.println("[X]  Error testing channel access: " + e.getMessage());
        }

        return false;
    }

    /**
     * Test if bot can read messages in a channel
     */
    public boolean testMessageAccess(String channelId, String messageId) {
        String url = "https://discord.com/api/v9/channels/" + channelId + "/messages/" + messageId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bot " + botToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();

            if (code == 200) {
                System.out.println("[OK] Bot can read messages in this channel");
                return true;
            } else if (code == 403) {
                System.out.println("[X]  Bot CANNOT read messages (403 Forbidden)");
                System.out.println("     The bot needs READ_MESSAGE_HISTORY permission");
                System.out.println("     Channel ID: " + channelId);
                if (response.body() != null) {
                    String body = response.body().string();
                    System.out.println("     Error: " + body);
                }
                return false;
            } else if (code == 404) {
                System.out.println("[X]  Message not found (404)");
                System.out.println("     This could mean:");
                System.out.println("     - Message ID is incorrect");
                System.out.println("     - Message was deleted");
                System.out.println("     - Bot doesn't have VIEW_CHANNEL permission");
                System.out.println("     Channel ID: " + channelId);
                System.out.println("     Message ID: " + messageId);
                return false;
            } else {
                System.out.println("[X]  Unexpected response: " + code);
                if (response.body() != null) {
                    String body = response.body().string();
                    System.out.println("     Response: " + body);
                }
                return false;
            }
        } catch (IOException e) {
            System.out.println("[X]  Error testing message access: " + e.getMessage());
        }

        return false;
    }

    /**
     * Run full diagnostic test
     */
    public boolean runFullDiagnostic(String channelId, String messageId) {
        System.out.println(" 1. Testing bot token...");
        boolean tokenValid = testBotToken();
        System.out.println();

        if (!tokenValid) {
            System.out.println("Bot token is invalid. Please check your configuration.");
            return false;
        }

        System.out.println(" 2. Testing channel access...");
        boolean channelAccess = testChannelAccess(channelId);
        System.out.println();

        if (!channelAccess) {
            System.out.println("Bot cannot access channel. Please invite bot to server and give it VIEW_CHANNEL permission.");
            return false;
        }

        System.out.println(" 3. Testing message access...");
        boolean messageAccess = testMessageAccess(channelId, messageId);
        System.out.println();

        if (!messageAccess) {
            System.out.println("Bot cannot read messages. Please give bot READ_MESSAGE_HISTORY permission or check if message with id '" + messageId + "' exists.");
            return false;
        }

        System.out.println("All tests passed!");
        return true;
    }
}
