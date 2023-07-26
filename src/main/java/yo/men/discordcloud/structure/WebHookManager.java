package yo.men.discordcloud.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import yo.men.discordcloud.Main;
import yo.men.discordcloud.gui.ProgressGUI;
import yo.men.discordcloud.utils.FileHashCalculator;
import yo.men.discordcloud.utils.FileHelper;
import yo.men.discordcloud.utils.FileMerger;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class WebHookManager implements AutoCloseable {

    private final String UPLOAD_WEBHOOK_URL;
    public final int MAX_FILE_SIZE;

    //Brak obsługi wysyłania plików, stosowane tylko do ich pobierania
    public WebHookManager(int maxPartSize) {
        UPLOAD_WEBHOOK_URL = null;
        MAX_FILE_SIZE = maxPartSize;
    }

    //Posiada obsługę pobierania i wysyłania plików
    public WebHookManager(String webhookUrl, int maxPartSize) {
        UPLOAD_WEBHOOK_URL = webhookUrl;
        MAX_FILE_SIZE = maxPartSize;
    }

    public boolean sendFile(File file) throws IOException {
        long partsCount = FileHelper.calculateMaxPartCount(file.getAbsolutePath());

        ProgressGUI progressGUI = new ProgressGUI(partsCount);
        OkHttpClient client = new OkHttpClient();

        for (int i = 0; i < partsCount; i++) {
            File partFile = FileHelper.getFilePart(file.getAbsolutePath(), i);

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", partFile.getName(),
                            RequestBody.create(MediaType.parse("application/octet-stream"), partFile))
                    .build();

            Request request = new Request.Builder()
                    .url(UPLOAD_WEBHOOK_URL)
                    .post(requestBody)
                    .build();

            boolean success = false;
            DiscordResponse discordResponse = null;
            int responseCode = -1;
            try (Response response = client.newCall(request).execute()) {
                responseCode = response.code();
                if (responseCode == 200 || responseCode == 201) {
                    success = true;
                    String responseBody = response.body().string();
                    Gson gson = new Gson();
                    discordResponse = gson.fromJson(responseBody, DiscordResponse.class);

                }
                //todo obsługa innych kodów
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                //mysle ze ten kod mozna dac poza blok finally
                if (success) {
                    if (discordResponse != null && discordResponse.getAttachments() != null && !discordResponse.getAttachments().isEmpty()) {
                        DiscordAttachment attachment = discordResponse.getAttachments().get(0);
                        String messageId = discordResponse.getId();
                        saveUploadedFile(partFile, file, messageId, attachment.getUrl(), success);
                        System.out.println("Wysłano plik: " + partFile.getName() + " (ID wiadomości: " + messageId + ")");
                        System.out.println("URL pliku: " + attachment.getUrl());
                        partFile.delete(); // usuwanie pliku tymczasowego

                        if (progressGUI.incrementProgress()) { // Sprawdzanie, czy zadanie zostało w całości zakończone
                            return true;
                        }
                    } else {
                        throw new RuntimeException("Invalid Discord response!");
                    }
                } else {
                    System.err.println("Wystąpił błąd podczas wysyłania pliku " + partFile.getName() + " (HTTP code: " + responseCode + ")");
                }
            }
        }
        return false; // Nie można było ukończyć zadania
    }

    private void saveUploadedFile(File partFile, File originalFile, String messageId, String url, boolean isSuccess) {
        DiscordFileStruct structure = FileHelper.loadStructureFile(new File(Main.STORAGE_DIR + originalFile.getName() + ".json")); //fixme: jest taki problem, że ścieżka originalFile nie prowadzi do storage/plik.xx.json tylko do jego głównej lokacji
        if (structure == null) {
            structure = new DiscordFileStruct(originalFile.getAbsolutePath(), FileHashCalculator.getFileHash(originalFile), new LinkedList<>());
        }
        System.out.println(originalFile);
        LinkedList<DiscordFilePart> uploadedFiles = structure.getParts();
        System.out.println(uploadedFiles.size());
        System.out.println("fff " + partFile);
        String hash = FileHashCalculator.getFileHash(partFile);
        uploadedFiles.add(new DiscordFilePart(partFile.getName(), hash, messageId, url, isSuccess));
        saveStructure(structure, uploadedFiles);
        System.out.println(uploadedFiles);
    }

    private void saveStructure(DiscordFileStruct structure, LinkedList<DiscordFilePart> uploadedFiles) {
        structure.setParts(uploadedFiles);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(structure);

        try (Writer writer = new FileWriter(structure.getOriginalName())) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean downloadFile(DiscordFileStruct structure) throws IOException {
        if (structure != null) {
            //long partsCount = FileHelper.calculateMaxPartCount(structure.getFixedFilePath());
            long partsCount = structure.getParts().size();
            partsCount = partsCount + 1; // +1 ponieważ jest jeszcze proces łączenia kawałków i zaliczam go jako jaden kawałek

            ProgressGUI progressGUI = new ProgressGUI(partsCount);

            OkHttpClient client = new OkHttpClient();
            final String downloadDir = ".temp/downloads/" + structure.getOriginalName() + "/";
            System.out.println(structure.getParts().size());

            for (DiscordFilePart part : structure.getParts()) {
                String downloadUrl = part.getUrl();

                Request request = new Request.Builder()
                        .url(downloadUrl)
                        .get()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    File out = new File(downloadDir);
                    out.mkdirs(); // tworzenie tymczasowego folderu do pobrania plików
                    out = new File(downloadDir + part.getName()); // zamiana na ścieżkę do pliku
                    System.out.println("downloaddir: " + downloadDir);
                    System.out.println("outputfilepath: " + downloadDir + part.getName());
                    // Zapis pliku na dysku
                    try (FileOutputStream fileOutputStream = new FileOutputStream(out)) {
                        fileOutputStream.write(response.body().bytes());
                        System.out.println("Pobrano plik: " + out);
                        //todo sprawdzanie sumy kontrolnej kawałka pliku
                        progressGUI.incrementProgress();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //todo: progress bar - trzeba zrobić oddzielny do pobierania (bo są dwa procesy: download i łączenie)
            }
            System.out.println("Pobrano pliki. Łączenie...");

            System.out.println(structure.getOriginalName());
            File finalFile = new File("downloads/");
            finalFile.mkdirs(); // tworzenie folderu na pobrany plik
            finalFile = new File("downloads/" + structure.getOriginalName()); // docelowy plik
            FileMerger.mergeFiles(downloadDir, finalFile);
            System.out.println("Plik został poprawnie połączony");
            System.out.println("Sprawdzanie sumy kontrolnej...");

            System.out.println(finalFile);
            String hash = FileHashCalculator.getFileHash(finalFile);

            if (hash.equals(structure.getSha256Hash())) {
                System.out.println("Plik został poprawnie pobrany");
                if (progressGUI.incrementProgress()) { // Sprawdzanie, czy zadanie zostało w całości zakończone
                    return true;
                }
            } else {
                System.out.println("Sumy kontrolne są różne:");
                System.out.println("Structure SHA256: " + structure.getSha256Hash());
                System.out.println("Downloaded file SHA256: " + hash);
            }

        }
        return false; // Nie można było ukończyć zadania
    }

    @Override
    public void close() {
        System.out.println("Closing WebHookManager");
    }
}
