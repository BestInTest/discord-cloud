package bo.wii.discordcloud.core.services.upload;

import bo.wii.discordcloud.core.Logger;
import bo.wii.discordcloud.core.exceptions.AuthorizationException;
import bo.wii.discordcloud.core.structure.enums.UploadType;
import bo.wii.discordcloud.core.utils.FileHelper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Uploader service that sends files via Bot
 */
public class BotUploaderService extends BaseUploaderService {

    private final String BOT_TOKEN;
    private final String CHANNEL_ID;
    private final OkHttpClient client;
    private final Gson gson;

    // Helper class to return both message ID and attachment URL
    private record UploadResult(String messageId, String attachmentUrl) {
    }

    public BotUploaderService(String botToken, String channelId, int chunkFileSize) {
        super(chunkFileSize);
        BOT_TOKEN = botToken;
        CHANNEL_ID = channelId;
        gson = new Gson();
        client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public boolean uploadSinglePart(File partFile, File originalFile) throws IOException, AuthorizationException {
        try {
            UploadResult result = uploadAttachmentToChannel(partFile);
            Logger.debug(BotUploaderService.class, "Uploaded part " + partFile.getName() + " to URL: " + (result != null ? result.attachmentUrl() : null));
            if (result != null) {
                FileHelper.saveUploadedFile(partFile, originalFile, result.messageId(), result.attachmentUrl(), true,
                                          getUploadType(), CHANNEL_ID, MAX_PART_SIZE);
                partFile.delete();
                return true;
            }
            return false;

        } catch (AuthorizationException e) {
            // Rethrow so the caller can handle it properly
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            Logger.err(BotUploaderService.class, "Failed to upload part: " + e.getMessage());
            FileHelper.saveUploadedFile(partFile, originalFile, "", "", false,
                                      getUploadType(), CHANNEL_ID, MAX_PART_SIZE);
            partFile.delete();
            return false;
        }
    }

    @Override
    protected UploadType getUploadType() {
        return UploadType.BOT;
    }

    @Override
    protected String getChannelId() {
        return CHANNEL_ID;
    }

    private String getAuthorizationHeader() {
        return "Bot " + BOT_TOKEN;
    }

    private UploadResult uploadAttachmentToChannel(File file) throws IOException, AuthorizationException {
        //todo: int uploadAttempts = 3;
        String uploadId = generateUploadId();
        JsonObject uploadInfo = requestUploadUrl(file, uploadId);

        if (uploadInfo == null) {
            throw new IOException("Failed to get upload info from Discord");
        }

        String uploadUrl = uploadInfo.get("upload_url").getAsString();
        String uploadFileName = uploadInfo.get("upload_filename").getAsString();

        boolean uploaded = uploadFileToUrl(file, uploadUrl);
        if (!uploaded) {
            throw new IOException("Failed to upload file to Discord CDN");
        }

        return finalizeUpload(file.getName(), uploadFileName, uploadId);
    }

    private JsonObject requestUploadUrl(File file, String uploadId) throws IOException, AuthorizationException {
        JsonObject payload = new JsonObject();
        JsonArray files = new JsonArray();
        JsonObject fileObj = new JsonObject();

        // Create payload
        fileObj.addProperty("file_size", file.length());
        fileObj.addProperty("filename", file.getName());
        fileObj.addProperty("id", uploadId);
        fileObj.addProperty("is_clip", false);
        fileObj.addProperty("original_content_type", "");
        files.add(fileObj);
        payload.add("files", files);

        //https://discord.com/developers/docs/resources/message#create-message
        RequestBody body = RequestBody.create(
                gson.toJson(payload),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url("https://discord.com/api/v9/channels/" + CHANNEL_ID + "/attachments")
                .addHeader("Authorization", getAuthorizationHeader())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            Logger.debug(BotUploaderService.class, "Request upload URL response code: " + response.code());
            if (response.code() == 401 || response.code() == 403) {
                throw new AuthorizationException("Invalid bot token or no access to channel");
            }

            if (!response.isSuccessful()) {
                Logger.err(BotUploaderService.class, "Failed to request upload URL: " + response.code());
                return null;
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            JsonArray attachments = json.getAsJsonArray("attachments");

            if (!attachments.isEmpty()) {
                return attachments.get(0).getAsJsonObject();
            }
        }
        return null;
    }

    private boolean uploadFileToUrl(File file, String uploadUrl) throws IOException {
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        Request request = new Request.Builder()
                .url(uploadUrl)
                .put(fileBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    private UploadResult finalizeUpload(String filename, String uploadFilename, String uploadId) throws IOException {
        JsonObject payload = new JsonObject();
        JsonArray attachments = new JsonArray();
        JsonObject attachment = new JsonObject();

        attachment.addProperty("id", uploadId);
        attachment.addProperty("filename", filename);
        attachment.addProperty("uploaded_filename", uploadFilename);
        attachment.addProperty("original_content_type", "");
        attachments.add(attachment);
        payload.add("attachments", attachments);
        payload.addProperty("content", "");

        RequestBody body = RequestBody.create(
                gson.toJson(payload),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url("https://discord.com/api/v9/channels/" + CHANNEL_ID + "/messages")
                .addHeader("Authorization", getAuthorizationHeader())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Logger.err(BotUploaderService.class, "Failed to finalize upload: " + response.code());
                return null;
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            // Get message ID
            String messageId = json.get("id").getAsString();

            // Get file URL
            JsonArray responseAttachments = json.getAsJsonArray("attachments");
            if (!responseAttachments.isEmpty()) {
                JsonObject att = responseAttachments.get(0).getAsJsonObject();
                String attachmentUrl = att.get("url").getAsString();

                return new UploadResult(messageId, attachmentUrl);
            }
        }
        return null;
    }

    // Random id between 400 and 3000 (based on my testing)
    // Must be string
    private String generateUploadId() {
        return String.valueOf(400 + (int)(Math.random() * ((3000 - 400) + 1)));
    }
}
