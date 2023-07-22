package yo.men.discordcloud.structure;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class DiscordResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("attachments")
    private List<DiscordAttachment> attachments;

    public String getId() {
        return id;
    }

    public List<DiscordAttachment> getAttachments() {
        return attachments;
    }
}
