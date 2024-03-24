package yo.men.discordcloud.utils;

import com.google.gson.Gson;
import okhttp3.Response;
import yo.men.discordcloud.structure.DiscordResponse;
import yo.men.discordcloud.structure.attachment.Param;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ApiUtil {

    public static DiscordResponse parseResponse(Response response) throws IOException {
        String responseBody = response.body().string();
        Gson gson = new Gson();
        return gson.fromJson(responseBody, DiscordResponse.class);
    }

    public static Map<Param, String> extractParameters(String link) {
        Map<Param, String> parameters = new HashMap<>();
        String[] parts = link.split("[?&]");
        for (String part : parts) {
            if (part.startsWith("http")) {
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
                System.err.println("Params keyValue != 2 (" + keyValue.length + "). Old link without params?");
            }
        }
        return parameters;
    }

    public static long hexToDecimal(String hex) {
        return Long.parseLong(hex, 16);
    }
}
