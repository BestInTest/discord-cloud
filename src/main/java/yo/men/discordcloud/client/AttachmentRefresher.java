package yo.men.discordcloud.client;

import yo.men.discordcloud.Logger;
import yo.men.discordcloud.structure.DiscordFilePart;
import yo.men.discordcloud.structure.DiscordFileStruct;
import yo.men.discordcloud.structure.DiscordResponse;
import yo.men.discordcloud.utils.FileHelper;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;

public class AttachmentRefresher extends WebhookClient {

    public AttachmentRefresher(String webhookUrl) {
        super(webhookUrl);
    }

    public void refreshAttachments(File structFile) throws IOException {
        DiscordFileStruct struct = FileHelper.loadStructureFile(structFile);
        if (struct != null && struct.isValid()) {
            for (DiscordFilePart part : struct.getParts()) {
                long messageId = Long.parseLong(part.getMessageId());
                DiscordResponse apiResponse = fetchFromApi(messageId);
                String refreshedUrl = apiResponse.getAttachments().get(0).getUrl();
                if (refreshedUrl != null) {
                    DiscordFilePart refreshedPart = new DiscordFilePart(part.getName(), part.getSha256Hash(), part.getMessageId(), refreshedUrl, true);

                    LinkedHashSet<DiscordFilePart> refreshedParts = new LinkedHashSet<>(struct.getParts());
                    refreshedParts.remove(part);
                    refreshedParts.add(refreshedPart);
                    FileHelper.saveStructure(struct, refreshedParts);
                } else {
                    Logger.err(this.getClass(), "Nie można odświeżyć linku (refreshedUrl = null)");
                }

            }
        }
    }
}
