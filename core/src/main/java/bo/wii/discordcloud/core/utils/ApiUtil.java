package bo.wii.discordcloud.core.utils;

import bo.wii.discordcloud.core.services.delete.DeleteTask;
import bo.wii.discordcloud.core.structure.FileStruct;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.structure.DiscordResponse;
import bo.wii.discordcloud.core.structure.attachment.Param;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ApiUtil {

    /**
     * Parses an API response (json) into a DiscordResponse
     * @param response the API response containing json
     */
    public static DiscordResponse parseResponse(@NotNull Response response) throws IOException {
        String responseBody = response.body().string();
        Gson gson = new Gson();
        return gson.fromJson(responseBody, DiscordResponse.class);
    }

    /**
     * Extracts parameters from a file download link.
     * To simplify usage, the map uses {@link Param} as key.
     * @param link the file download link
     * @return a map containing the link parameters
     */
    public static Map<Param, String> extractParameters(String link) {
        Map<Param, String> parameters = new HashMap<>();
        String[] parts = link.split("[?&]");
        for (String part : parts) {
            if (part.startsWith("http")) { // Ignore 1st element
                continue;
            }
            String[] keyValue = part.split("=");
            if (keyValue.length == 2) {
                switch (keyValue[0]) {
                    case "is":
                        parameters.put(Param.ISSUE_TIMESTAMP_HEX, keyValue[1]);
                        break;
                    case "ex":
                        parameters.put(Param.EXPIRE_TIMESTAMP_HEX, keyValue[1]);
                        break;
                    case "hm":
                        parameters.put(Param.SIGNATURE, keyValue[1]);
                        break;
                    default:
                        parameters.put(Param.UNKNOWN, keyValue[1]);
                        break;
                }
            } else {
                Logger.err(ApiUtil.class, "Params keyValue != 2 (" + keyValue.length + "). Old link without params?");
            }
        }
        return parameters;
    }

    public static long hexToDecimal(String hex) {
        return Long.parseLong(hex, 16);
    }

    /**
     * Validates that the provided webhook URL belongs to the same channel
     * as the file structure. Returns an error message if validation fails, null if OK.
     */
    public static String validateWebhookChannel(FileStruct structure, String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("your_webhook")) {
            return "Webhook URL is not configured";
        }

        String structureChannelId = structure.getEffectiveChannelId();
        if (structureChannelId == null || structureChannelId.isEmpty()) {
            Logger.info(DeleteTask.class, "Cannot determine channel ID from structure. Skipping webhook channel validation");
            return null;
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(webhookUrl)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            if (code == 401 || code == 403) {
                return "Invalid webhook URL (HTTP " + code + "): check your webhook configuration";
            }
            if (code == 404) {
                return "Webhook not found (HTTP 404): the webhook may have been deleted";
            }
            if (code != 200) {
                return "Unexpected response from Discord while validating webhook (HTTP " + code + ")";
            }

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (!json.has("channel_id")) {
                Logger.info(DeleteTask.class, "Webhook response does not contain channel_id. Skipping validation");
                return null;
            }

            String webhookChannelId = json.get("channel_id").getAsString();

            if (!webhookChannelId.equals(structureChannelId)) {
                return "Webhook channel mismatch!\n"
                        + "File was uploaded to channel: " + structureChannelId + "\n"
                        + "Selected webhook belongs to channel: " + webhookChannelId + "\n"
                        + "Please select the correct webhook profile.";
            }

            Logger.debug(DeleteTask.class, "Webhook channel validation passed: channel " + webhookChannelId);
            return null;

        } catch (IOException e) {
            Logger.error(DeleteTask.class, "Failed to validate webhook: " + e.getMessage());
            return "Could not connect to Discord to validate webhook: " + e.getMessage();
        }
    }
}
