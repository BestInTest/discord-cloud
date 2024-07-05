package yo.men.discordcloud.structure;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import yo.men.discordcloud.structure.attachment.DiscordAttachment;

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
     * Zwraca id wiadomości. Jeżeli nie odnaleziono
     * wiadomości, zwraca null (dzieje się to również,
     * wtedy gdy wiadomość istnieje, ale webhook jest niepoprawny
     * lub nie ma on dostępu do tej wiadomości).
     * @return id wiadomości.
     */
    @Nullable
    public String getId() {
        return id;
    }

    /**
     * Zwraca plik(i) zawarte w wiadomości. Jeżeli nie ma plików
     * lub nie odnaleziono wiadomości, zwraca null.
     * @return plik(i) zawarte w wiadomości.
     */
    @Nullable
    public List<DiscordAttachment> getAttachments() {
        return attachments;
    }


    /**
     * Zwraca znaczenie błędu api. Jeżeli nie ma błędu, zwraca null.
     * @return znaczenie błędu api.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Zwraca kod błędu api określony w https://discord.com/developers/docs/topics/opcodes-and-status-codes#json
     * Jeżeli nie wystąpił błąd, zwraca -1
     * @return kod błędu api.
     */
    public int getCode() {
        return code;
    }
}
