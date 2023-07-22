package yo.men.discordcloud.structure;

import com.google.gson.annotations.SerializedName;

public class DiscordAttachment {
    @SerializedName("filename")
    private String filename;

    @SerializedName("url")
    private String url;

    public String getFilename() {
        return filename;
    }

    public String getUrl() {
        return url;
    }
}
