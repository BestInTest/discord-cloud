package bo.wii.discordcloud.core.services.upload;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.structure.enums.UploadType;
import okhttp3.*;
import bo.wii.discordcloud.core.structure.DiscordResponse;
import bo.wii.discordcloud.core.structure.attachment.DiscordAttachment;
import bo.wii.discordcloud.core.utils.ApiUtil;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.core.exceptions.AuthorizationException;
import bo.wii.discordcloud.core.exceptions.FileTooLargeException;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;

public class UploaderService extends BaseUploaderService {

    private final String WEBHOOK_URL;

    public UploaderService(String webhook, int chunkFileSize) {
        super(chunkFileSize);
        WEBHOOK_URL = webhook;
    }


    /**
     * Uploads a single part file and saves the result
     * @param partFile The part file to upload
     * @param originalFile The original file
     * @return true if successful, false otherwise
     */
    @Override
    public boolean uploadSinglePart(File partFile, File originalFile) throws IOException, InterruptedException, AuthorizationException, FileTooLargeException {
        DiscordResponse discordResponse = uploadAttachment(partFile);

        if (discordResponse != null && discordResponse.getAttachments() != null && !discordResponse.getAttachments().isEmpty()) {
            DiscordAttachment attachment = discordResponse.getAttachments().get(0);
            String messageId = discordResponse.getId();
            FileHelper.saveUploadedFile(partFile, originalFile, messageId, attachment.getUrl(), true, MAX_PART_SIZE);
            partFile.delete(); // delete temp file
            return true;
        }
        return false;
    }

    @Override
    protected UploadType getUploadType() {
        return UploadType.WEBHOOK;
    }

    @Override
    protected String getChannelId() {
        return null; // Webhook doesn't use channel ID
    }

    private DiscordResponse uploadAttachment(File partFile) throws AuthorizationException, FileTooLargeException, IOException, InterruptedException {
        Logger.log(UploaderService.class, "Uploading attachment: " + partFile.getName());

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", partFile.getName(),
                        RequestBody.create(MediaType.parse("application/octet-stream"), partFile))
                .build();

        Request request = new Request.Builder()
                .url(WEBHOOK_URL)
                .post(requestBody)
                .build();

        OkHttpClient client = new OkHttpClient();
        DiscordResponse discordResponse = null;
        int responseCode = -1;
        boolean success = false;

        // Retry up to 5 times until success or Discord returns a response to discordResponse
        for (int attemps = 0; (attemps < 5) && (discordResponse == null); attemps++) {
            try (Response response = client.newCall(request).execute()) { // automatically closes connections
                responseCode = response.code();
                Logger.log(this.getClass(), "response: " + responseCode);

                //too many requests
                if (responseCode == 429) {
                    // Wait for rate limit to reset and retry
                    Thread.sleep(5000); // 5s might not be enough
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

                //file too large
                if (responseCode == 413) {
                    // The configured maximum chunk size is too large
                    throw new FileTooLargeException("File too large: HTTP code 413");
                }

                //bad webhook
                if (responseCode == 401) {
                    throw new AuthorizationException("Bad webhook link: HTTP code 401");
                }

                if (responseCode == 200 || responseCode == 201) {
                    success = true;
                    discordResponse = ApiUtil.parseResponse(response);
                    break;
                }

                //nieznany błąd pomimo prób wysyłania
                Logger.err(this.getClass(), "Unhandled HTTP code occurred: " + responseCode);
            } catch (SocketTimeoutException ignored) {
                Logger.err(this.getClass(), "Socket timeout while uploading file "  + partFile.getName());
            }
        }

        return discordResponse;

    }
}
