package bo.wii.discordcloud.core.structure;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import bo.wii.discordcloud.core.structure.attachment.DiscordAttachment;

import java.util.List;

public class DiscordResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("attachments")
    private List<DiscordAttachment> attachments;

    //for errors
    @SerializedName("message")
    private String errorMessage;
    @SerializedName("code")
    private int code = -1;

    /**
     * Returns the message ID. If the message was not found,
     * returns null (this also happens when the message exists,
     * but the webhook is invalid or does not have access to the message).
     * @return the message ID or null.
     */
    @Nullable
    public String getId() {
        return id;
    }

    /**
     * Returns the file(s) contained in the message. If there are no files
     * or the message was not found, returns null.
     * @return the file(s) contained in the message or null.
     */
    @Nullable
    public List<DiscordAttachment> getAttachments() {
        return attachments;
    }


    /**
     * Returns the API error message. If there is no error, returns null.
     * @return the API error message.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns the API error code as specified in the <a href="https://discord.com/developers/docs/topics/opcodes-and-status-codes#json">documentation</a>.
     * If no error occurred, returns -1.
     * @return the API error code.
     */
    public int getCode() {
        return code;
    }
}
