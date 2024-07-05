package yo.men.discordcloud.client;

import yo.men.discordcloud.Logger;
import yo.men.discordcloud.structure.attachment.DiscordAttachment;
import yo.men.discordcloud.structure.enums.ApiJsonCodes;
import yo.men.discordcloud.structure.DiscordFilePart;
import yo.men.discordcloud.structure.DiscordFileStruct;
import yo.men.discordcloud.structure.DiscordResponse;
import yo.men.discordcloud.utils.FileHelper;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

public class AttachmentRefresher extends WebhookClient {

    //todo: zrobić gui (progressbar) może nawet w tej klasie

    public AttachmentRefresher(String webhookUrl) {
        super(webhookUrl);
    }

    public void refreshAttachments(File structFile) throws IOException {
        DiscordFileStruct struct = FileHelper.loadStructureFile(structFile);
        if (struct != null && struct.isValid()) {
            for (DiscordFilePart part : struct.getParts()) {
                long messageId = Long.parseLong(part.getMessageId());
                DiscordResponse apiResponse = fetchFromApi(messageId);
                if (apiResponse.getCode() != ApiJsonCodes.UNKNOWN_MESSAGE.getCode()) {

                    //todo: w celu optymalizacji można dodać sprawdzanie czy trzeba odświeżyć link (sprawdzić czy może jeszcze jest ważny)
                    List<DiscordAttachment> attachments = apiResponse.getAttachments();
                    if (attachments != null && !attachments.isEmpty()) {
                        if (attachments.size() == 1) {
                            String refreshedUrl = attachments.get(0).getUrl();
                            DiscordFilePart refreshedPart = new DiscordFilePart(part.getName(), part.getSha256Hash(), part.getMessageId(), refreshedUrl, true);

                            LinkedHashSet<DiscordFilePart> refreshedParts = new LinkedHashSet<>(struct.getParts());
                            refreshedParts.remove(part);
                            refreshedParts.add(refreshedPart);
                            FileHelper.saveStructure(struct, refreshedParts);
                        } else {
                            Logger.err(this.getClass(), "Wiadomość zawiera więcej niż jeden plik (Id: " + messageId + ")");
                        }
                    } else {
                        Logger.err(this.getClass(), "Nie można odświeżyć linku. Wiadomość nie zawiera plików (Id: " + messageId + ")");
                        JOptionPane.showMessageDialog(null,
                                "Nie można odśwież linku. Wiadomość nie zawiera plików (Id: " + messageId + ")", "Błąd", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    Logger.err(this.getClass(), "Nie odnaleziono wiadomości (Id: " + messageId + ")");
                    JOptionPane.showMessageDialog(null,
                            "Nie odnaleziono wiadomości (Id: " + messageId + ")", "Błąd", JOptionPane.ERROR_MESSAGE);
                }

            }
        } else {
            Logger.err(this.getClass(), "Invalid structure");
            JOptionPane.showMessageDialog(null,
                    "Invalid structure", "Błąd", JOptionPane.ERROR_MESSAGE);
        }
    }
}
