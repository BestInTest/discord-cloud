package yo.men.discordcloud.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import yo.men.discordcloud.Main;
import yo.men.discordcloud.gui.ProgressGUI;
import yo.men.discordcloud.utils.FileHashCalculator;
import yo.men.discordcloud.utils.FileHelper;
import yo.men.discordcloud.utils.FileMerger;

import javax.swing.*;
import java.io.*;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Stack;

public class WebHookManager {

    private final String UPLOAD_WEBHOOK_URL;
    public final int MAX_FILE_SIZE;

    private Thread runningThread;

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

    public void uploadFile(File file) throws IOException {
        if (runningThread != null) {
            JOptionPane.showMessageDialog(null,
                    "Wystąpił nieoczekiwany błąd. \nSzczegóły błędu: Thread is already running!", "Błąd", JOptionPane.ERROR_MESSAGE);
            return;
        }

        long partsCount = FileHelper.calculateMaxPartCount(file.getAbsolutePath(), Main.CHUNK_FILE_SIZE);

        ProgressGUI progressGUI = new ProgressGUI(partsCount, this);
        OkHttpClient client = new OkHttpClient();

        runningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < partsCount; i++) {
                        File partFile = FileHelper.getFilePart(file.getAbsolutePath(), i, Main.CHUNK_FILE_SIZE);

                        RequestBody requestBody = new MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("file", partFile.getName(),
                                        RequestBody.create(MediaType.parse("application/octet-stream"), partFile))
                                .build();

                        Request request = new Request.Builder()
                                .url(UPLOAD_WEBHOOK_URL)
                                .post(requestBody)
                                .build();

                        DiscordResponse discordResponse = null;
                        int responseCode = -1;
                        boolean success = false;

                        //próbowanie 5 razy aż będzie "success"
                        for (int attemps = 0; (attemps < 5) && (!success); attemps++) {
                            try (Response response = client.newCall(request).execute()) { // dzięki temu automatycznie zamyka połączenia
                                responseCode = response.code();
                                System.out.println("response: " + responseCode);

                                //too many requests
                                if (responseCode == 429) {
                                    //czekanie aż zejdzie rate limit i ponawianie próby
                                    Thread.sleep(5000); // nie wiem czy 5s to nie za mało
                                    continue;
                                }

                                //file too large
                                if (responseCode == 413) {
                                    System.err.println("File too large: HTTP code 413");
                                    JOptionPane.showMessageDialog(null,
                                            "Ustawiona maksymalna wielkość kawałka pliku jest za duża. \nSzczegóły błędu: HTTP code 413", "Błąd", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                //bad webhook
                                if (responseCode == 401) {
                                    System.err.println("Bad webhook link");
                                    JOptionPane.showMessageDialog(null,
                                            "Sprawdź czy ustawiony webhook jest poprawny. \nSzczegóły błędu: HTTP code 401", "Błąd", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                if (responseCode == 200 || responseCode == 201) {
                                    success = true;
                                    discordResponse = parseResponse(response);
                                    break;
                                }

                                //nieznany błąd pomimo prób wysyłania
                                System.err.println("Wystąpił nieznany błąd HTTP: " + responseCode);
                            } catch (Exception e) {
                                e.printStackTrace();
                                //todo powiadomienie i przerwanie wysyłania
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
                                            progressGUI.dispose();
                                        }
                                    } else {
                                        //nie wiem czy tutaj dawać progressGUI.dispose();
                                        JOptionPane.showMessageDialog(null,
                                                "Wystąpił nieoczekiwany błąd. \nSzczegóły błędu: Invalid Discord response!", "Błąd", JOptionPane.ERROR_MESSAGE);
                                        throw new RuntimeException("Invalid Discord response!");
                                    }
                                } else {
                                    saveUploadedFile(partFile, file, null, null, success);

                                    if (responseCode != 429) { // ignore too many requests error
                                        JOptionPane.showMessageDialog(null,
                                                "Wystąpił błąd podczas wysyłania pliku " + partFile.getName() + "\nHTTP code: " + responseCode + "\n\nProszę ponowić wysyłanie pliku", "Błąd", JOptionPane.ERROR_MESSAGE);
                                        System.err.println("Wystąpił błąd podczas wysyłania pliku " + partFile.getName() + " (HTTP code: " + responseCode + ")");
                                        progressGUI.dispose();
                                        forceClose(true);
                                        return;
                                    }
                                }
                            }
                        }


                    }
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                            "Nie można wysłać pliku. Upewnij się że ustawiony webhook jest poprawny. \nSzczegóły błędu: \n" + e.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    progressGUI.dispose();
                    forceClose(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                            "Wystąpił nieoczekiwany błąd. \nSzczegóły błędu: \n" + e.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    progressGUI.dispose();
                    forceClose(true);
                }
                Main.getStartGUI().setVisible(true); // pokazywanie głównego okna po zakończeniu operacji
            }
        });
        runningThread.start();
    }


    /*
    Kontynuuje wysyłanie pliku.
     */
    public void resumeUploading(File file, DiscordFileStruct struct) {
        if (!struct.isValid()) {
            System.err.println("Invalid structure");
            JOptionPane.showMessageDialog(null,
                    "Invalid structure", "Błąd", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //sprawdzanie czy pliki są identyczne
        if (!file.getName().equals(struct.getOriginalFileName())) {
            JOptionPane.showMessageDialog(null,
                    "Nazwa oryginalnego pliku została zmieniona!", "Błąd", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String hash = FileHashCalculator.getFileHash(file);
        if (!hash.equals(struct.getSha256Hash())) {
            JOptionPane.showMessageDialog(null,
                    "Suma kontrolna pliku jest inna niż oczekiwana.", "Błąd", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (runningThread != null) {
            JOptionPane.showMessageDialog(null,
                    "Wystąpił nieoczekiwany błąd. \nSzczegóły błędu: Thread is already running!", "Błąd", JOptionPane.ERROR_MESSAGE);
            return;
        }

        long totalPartsCount = FileHelper.calculateMaxPartCount(file.getAbsolutePath(), struct.getSinglePartSize());
        int badPartsCount = extractBadParts(struct.getParts()).size();
        int uploadedPartsCount = struct.getParts().size() - badPartsCount;

        //sprawdzanie czy plik został już w pełni wysłany
        if (uploadedPartsCount == totalPartsCount) {
            JOptionPane.showMessageDialog(null,
                    "Plik został już w pełni przesłany", "Błąd", JOptionPane.ERROR_MESSAGE);
            return;
        }
        //uszkodzony plik
        if (uploadedPartsCount > totalPartsCount) {
            JOptionPane.showMessageDialog(null,
                    "Plik struktury jest uszkodzony lub wybrano niepoprawny plik.\n" +
                            "Ilość wysłanych kawałków jest większa niż całkowita możliwa liczba kawałków wybranego pliku.", "Błąd", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //sprawdzanie czy trzeba ponowić próbę wysyłania niektórych kawałków
        Stack<DiscordFilePart> badUploads = new Stack<>();
        for (DiscordFilePart part : struct.getParts()) {
            if (!part.isSuccess()) {
                badUploads.add(part);
            }
        }

        System.out.println("bad uploads: " + badUploads.size());
        long finalTotalPartsCount = totalPartsCount + badUploads.size(); // potrzebne ponieważ wątek wymaga stałej (finalnej) wartości zmiennej

        ProgressGUI progressGUI = new ProgressGUI(totalPartsCount - uploadedPartsCount, this);
        OkHttpClient client = new OkHttpClient();

        runningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("uploaded < total - " + (uploadedPartsCount < finalTotalPartsCount));
                    for (int partNum = uploadedPartsCount; partNum < finalTotalPartsCount; partNum++) {
                        File partFile;
                        if (badUploads.isEmpty()) {
                            //standardowa kontynuacja wysyłania
                            partFile = FileHelper.getFilePart(file.getAbsolutePath(), partNum, struct.getSinglePartSize());
                        } else {
                            //powtarzanie wysyłania pliku
                            DiscordFilePart badPart = badUploads.pop();
                            partFile = FileHelper.getFilePart(file.getAbsolutePath(), badPart.getPartNumber(), struct.getSinglePartSize());
                        }
                        System.out.println("uploading part " + partNum);

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
                        //próbowanie 5 razy aż będzie "success"
                        for (int attemps = 0; (attemps < 5) && (!success); attemps++) {
                            try (Response response = client.newCall(request).execute()) { // dzięki temu automatycznie zamyka połączenia
                                responseCode = response.code();
                                System.out.println("response: " + responseCode);

                                //too many requests
                                if (responseCode == 429) {
                                    //czekanie aż zejdzie rate limit i ponawianie próby
                                    Thread.sleep(5000); // nie wiem czy 5s to nie za mało
                                    continue;
                                }

                                //file too large
                                if (responseCode == 413) {
                                    System.err.println("File too large: HTTP code 413");
                                    JOptionPane.showMessageDialog(null,
                                            "Ustawiona maksymalna wielkość kawałka pliku jest za duża. \nSzczegóły błędu: HTTP code 413", "Błąd", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                //bad webhook
                                if (responseCode == 401) {
                                    System.err.println("Bad webhook link");
                                    JOptionPane.showMessageDialog(null,
                                            "Sprawdź czy ustawiony webhook jest poprawny. \nSzczegóły błędu: HTTP code 401", "Błąd", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                //todo obsługa innych kodów

                                if (responseCode == 200 || responseCode == 201) {
                                    success = true;
                                    discordResponse = parseResponse(response);
                                    break;
                                }

                                //nieznany błąd pomimo prób wysyłania
                                System.err.println("Wystąpił nieznany błąd HTTP: " + responseCode);
                            } catch (Exception e) {
                                e.printStackTrace();
                                //todo powiadomienie i przerwanie wysyłania
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
                                            progressGUI.dispose();
                                        }
                                    } else {
                                        //nie wiem czy tutaj dawać progressGUI.dispose();
                                        JOptionPane.showMessageDialog(null,
                                                "Wystąpił nieoczekiwany błąd. \nSzczegóły błędu: Invalid Discord response!", "Błąd", JOptionPane.ERROR_MESSAGE);
                                        throw new RuntimeException("Invalid Discord response!");
                                    }
                                } else {
                                    saveUploadedFile(partFile, file, null, null, success);

                                    if (responseCode != 429) { // ignore too many requests error
                                        JOptionPane.showMessageDialog(null,
                                                "Wystąpił błąd podczas wysyłania pliku " + partFile.getName() + "\nHTTP code: " + responseCode + "\n\nProszę ponowić wysyłanie pliku", "Błąd", JOptionPane.ERROR_MESSAGE);
                                        System.err.println("Wystąpił błąd podczas wysyłania pliku " + partFile.getName() + " (HTTP code: " + responseCode + ")");
                                        progressGUI.dispose();
                                        forceClose(true);
                                        return;
                                    }
                                }
                            }
                        }


                    }
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                            "Nie można wysłać pliku. Upewnij się że ustawiony webhook jest poprawny. \nSzczegóły błędu: \n" + e.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    progressGUI.dispose();
                    forceClose(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                            "Wystąpił nieoczekiwany błąd. \nSzczegóły błędu: \n" + e.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    progressGUI.dispose();
                    forceClose(true);
                }
                Main.getStartGUI().setVisible(true); // pokazywanie głównego okna po zakończeniu operacji
            }
        });
        runningThread.start();
    }

    private DiscordResponse parseResponse(Response response) throws IOException {
        String responseBody = response.body().string();
        Gson gson = new Gson();
        return gson.fromJson(responseBody, DiscordResponse.class);
    }

    private void saveUploadedFile(File partFile, File originalFile, String messageId, String url, boolean isSuccess) throws IOException {
        DiscordFileStruct structure = FileHelper.loadStructureFile(new File(originalFile.getName() + ".json"));
        if (structure == null) {
            structure = new DiscordFileStruct(originalFile.getAbsolutePath(), FileHashCalculator.getFileHash(originalFile), new LinkedHashSet<>());
        }
        LinkedHashSet<DiscordFilePart> uploadedFiles = structure.getParts();

        String hash = FileHashCalculator.getFileHash(partFile);
        DiscordFilePart oldBadPart = getBadPart(partFile.getName(), uploadedFiles); // kawałek który nie mógł zostać wysłany
        DiscordFilePart newPart = new DiscordFilePart(partFile.getName(), hash, messageId, url, isSuccess);

        /*
        Jeżeli jest wysyłany jakiś kawałek pliku, który nie mógł zostać
        wcześniej wysłany, to trzeba usunąć go z listy kawałków i dodać ten poprawny.
         */
        if (oldBadPart != null) {
            uploadedFiles.remove(oldBadPart); //fixme: nie usuwa (w pliku cały czas pozostaje wartość)
        }
        uploadedFiles.add(newPart);

        saveStructure(structure, uploadedFiles);
    }

    private void saveStructure(DiscordFileStruct structure, LinkedHashSet<DiscordFilePart> uploadedFiles) {
        structure.setParts(uploadedFiles);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(structure);

        File targetStructFile = new File(structure.getOriginalFileName() + ".json");
        try (Writer writer = new FileWriter(targetStructFile)) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private DiscordFilePart getBadPart(String partFileName, LinkedHashSet<DiscordFilePart> partsList) {
        for (DiscordFilePart part : partsList) {
            if (part.getName().equals(partFileName)) {
                return part;
            }
        }
        return null;
    }

    private Set<DiscordFilePart> extractBadParts(LinkedHashSet<DiscordFilePart> allParts) {
        Set<DiscordFilePart> badParts = new HashSet<>();
        for (DiscordFilePart part : allParts) {
            if (!part.isSuccess()) {
                badParts.add(part);
            }
        }
        return badParts;
    }

    public void downloadFile(DiscordFileStruct structure) {
        if (structure != null) {
            File alreadyDownloaded = new File("downloads/" + structure.getOriginalFileName());
            if (alreadyDownloaded.exists()) {
                JOptionPane.showMessageDialog(null,
                        "Znaleziono już istniejący plik w lokalizacji pobierania.", "Błąd", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!structure.isValid()) {
                System.err.println("Invalid structure");
                JOptionPane.showMessageDialog(null,
                        "Invalid structure", "Błąd", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!extractBadParts(structure.getParts()).isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        "Ten plik jest uszkodzony lub nie został poprawnie wysłany.", "Błąd", JOptionPane.ERROR_MESSAGE);
                return;
            }

            long partsCount = structure.getParts().size();
            partsCount = partsCount + 1; // +1 ponieważ jest jeszcze proces łączenia kawałków i zaliczam go jako jaden kawałek

            ProgressGUI progressGUI = new ProgressGUI(partsCount, this);

            OkHttpClient client = new OkHttpClient();
            final String downloadDir = ".temp/downloads/" + structure.getOriginalFileName() + "/";
            System.out.println("structure size: " + structure.getParts().size());

            if (runningThread != null) {
                JOptionPane.showMessageDialog(null,
                        "Wystąpił nieoczekiwany błąd. \nSzczegóły błędu: Thread is already running!", "Błąd", JOptionPane.ERROR_MESSAGE);
                return;
            }
            runningThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    File out = new File(downloadDir);
                    out.mkdirs(); // tworzenie tymczasowego folderu do pobrania plików

                    for (DiscordFilePart part : structure.getParts()) {
                        String downloadUrl = part.getUrl();

                        Request request = new Request.Builder()
                                .url(downloadUrl)
                                .get()
                                .build();

                        int responseCode = -1;
                        boolean success = false;

                        //próbowanie 5 razy aż będzie "success"
                        for (int attemps = 0; (attemps < 5) && (!success); attemps++) {
                            try (Response response = client.newCall(request).execute()) {
                                responseCode = response.code();
                                System.out.println("response: " + responseCode);

                                //too many requests
                                if (responseCode == 429) {
                                    //czekanie aż zejdzie rate limit i ponawianie próby
                                    Thread.sleep(5000); // nie wiem czy 5s to nie za mało
                                    continue;
                                }

                                //file too large
                                if (responseCode == 404) {
                                    System.err.println("File not found: HTTP code 404");
                                    JOptionPane.showMessageDialog(null,
                                            "Nie można ukończyć pobierania. Nie odnaleziono pliku " + part.getName() + "\nSzczegóły błędu: HTTP code 404", "Błąd", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                if (responseCode == 200 || responseCode == 201) {
                                    success = true;

                                    out = new File(downloadDir + part.getName()); // zamiana na ścieżkę do pliku
                                    System.out.println("download output: " + downloadDir + part.getName());

                                    // Zapis pliku na dysku
                                    try (FileOutputStream fileOutputStream = new FileOutputStream(out)) {
                                        fileOutputStream.write(response.body().bytes());
                                        System.out.println("Pobrano plik: " + out);

                                        String downloadedPartHash = FileHashCalculator.getFileHash(out);
                                        if (downloadedPartHash.equals(part.getSha256Hash())) {
                                            progressGUI.incrementProgress();
                                        } else {
                                            int dialogResult = JOptionPane.showConfirmDialog(null,
                                                    "Suma kontrola pliku " + part.getName() + " jest niepoprawna. \nCzy mimo to chcesz kontynuować?", "Błąd", JOptionPane.YES_NO_OPTION);
                                            if (dialogResult == JOptionPane.YES_OPTION) {
                                                continue;
                                            } else {
                                                progressGUI.dispose();
                                                forceClose(true);
                                            }
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    continue;
                                }

                                //nieznany błąd pomimo prób pobierania
                                System.err.println("Wystąpił nieznany błąd HTTP: " + responseCode);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    System.out.println("Pobrano pliki. Łączenie...");

                    try {
                        File finalFile = new File("downloads/");
                        finalFile.mkdirs(); // tworzenie folderu na pobrany plik
                        finalFile = new File("downloads/" + structure.getOriginalFileName()); // docelowy plik
                        FileMerger.mergeFiles(downloadDir, finalFile);
                        System.out.println("Plik został poprawnie połączony");
                        System.out.println("Sprawdzanie sumy kontrolnej...");

                        System.out.println(finalFile);
                        String hash = FileHashCalculator.getFileHash(finalFile);

                        if (hash.equals(structure.getSha256Hash())) {
                            System.out.println("Plik został poprawnie pobrany");
                            if (progressGUI.incrementProgress()) { // Sprawdzanie, czy zadanie zostało w całości zakończone
                                progressGUI.dispose();
                            }
                        } else {
                            System.out.println("Sumy kontrolne są różne:");
                            System.out.println("Structure SHA256: " + structure.getSha256Hash());
                            System.out.println("Downloaded file SHA256: " + hash);
                            JOptionPane.showMessageDialog(null,
                                    "Podczas łączenia plików wystąpił błąd. \nSzczegóły błędu: sumy kontrolne są różne", "Błąd", JOptionPane.ERROR_MESSAGE);
                            progressGUI.dispose();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(null,
                                "Podczas łączenia plików wystąpił błąd. \nSzczegóły błędu: \n" + e.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                        progressGUI.dispose();
                    }
                    Main.getStartGUI().setVisible(true); // pokazywanie głównego okna po zakończeniu operacji
                }
            });
            runningThread.start();

        }
    }

    public void forceClose(boolean showStartGUI) {
        if (runningThread != null) {
            System.out.println("Stopping runningThread...");
            runningThread.interrupt();
        } else {
            System.err.println("Thread is not running");
        }

        if (showStartGUI) {
            Main.getStartGUI().setVisible(true);
        }
    }
}
