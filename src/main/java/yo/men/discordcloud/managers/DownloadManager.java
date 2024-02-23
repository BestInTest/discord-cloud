package yo.men.discordcloud.managers;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import yo.men.discordcloud.structure.DiscordFilePart;
import yo.men.discordcloud.structure.DiscordFileStruct;
import yo.men.discordcloud.utils.FileHashCalculator;
import yo.men.discordcloud.utils.FileMerger;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DownloadManager {

    public final int MAX_FILE_SIZE;

    private List<Future<Result>> futures = new ArrayList<>();
    private final ExecutorService es;

    //Brak obsługi wysyłania plików, stosowane tylko do ich pobierania
    public DownloadManager(int maxPartSize, int threads) {
        MAX_FILE_SIZE = maxPartSize;
        es = Executors.newFixedThreadPool(threads);
    }

    public void download(DiscordFileStruct structure, String downloadDir) {
        for (DiscordFilePart partFile : structure.getParts()) {
            futures.add(downloadFile(es, partFile, downloadDir));
        }
        es.shutdown();

        System.out.println("Pobrano pliki. Łączenie...");

        try {
            File finalFile = new File(downloadDir);
            finalFile.mkdirs(); // tworzenie folderu na pobrany plik (ta sama zmienna jest później używana do zapisu pliku)
            finalFile = new File("downloads/" + structure.getOriginalFileName()); // docelowy plik
            FileMerger.mergeFiles(downloadDir, finalFile);
            System.out.println("Plik został poprawnie połączony");
            System.out.println("Sprawdzanie sumy kontrolnej...");

            System.out.println(finalFile);
            String hash = FileHashCalculator.getFileHash(finalFile);

            if (hash.equals(structure.getSha256Hash())) {
                System.out.println("Plik został poprawnie pobrany");
                //if (progressGUI.incrementProgress()) { // Sprawdzanie, czy zadanie zostało w całości zakończone
                //    progressGUI.dispose();
                //}
            } else {
                System.out.println("Sumy kontrolne są różne:");
                System.out.println("Structure SHA256: " + structure.getSha256Hash());
                System.out.println("Downloaded file SHA256: " + hash);
                JOptionPane.showMessageDialog(null,
                        "Podczas łączenia plików wystąpił błąd. \nSzczegóły błędu: sumy kontrolne są różne", "Błąd", JOptionPane.ERROR_MESSAGE);
                //progressGUI.dispose();
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Podczas łączenia plików wystąpił błąd. \nSzczegóły błędu: \n" + e.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
            //progressGUI.dispose();

            //pobieranie powinno działać, trzeba zająć się tym jak przekazywać postęp do gui
        }
    }

    private Future<Result> downloadFile(final ExecutorService es, DiscordFilePart fileToDl, String downloadDir) {
        return es.submit(new Callable<Result>() {
            @Override
            public Result call() {
                File out = new File(downloadDir);
                out.mkdirs(); // tworzenie tymczasowego folderu do pobrania plików

                String downloadUrl = fileToDl.getUrl();

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(downloadUrl)
                        .get()
                        .build();

                int responseCode = -1;
                boolean success = false;
                Result downloadResult = null;

                //próbowanie 5 razy aż będzie "success"
                for (int attemps = 0; (attemps < 5) && (!success); attemps++) {
                    try (Response response = client.newCall(request).execute()) {
                        responseCode = response.code();
                        System.out.println("response: " + responseCode);

                        //too many requests
                        if (responseCode == 429) {
                            downloadResult = new Result(false, responseCode, "Too many requests");
                            //czekanie aż zejdzie rate limit i ponawianie próby
                            Thread.sleep(5000); // nie wiem czy 5s to nie za mało
                            continue;
                        }

                        //Gateway unavailable
                        if (responseCode == 502) {
                            downloadResult = new Result(false, responseCode, "Gateway unavailable");
                            /*
                            Według dokumentacji wystarczy poczekać i spróbować ponownie.
                            https://discord.com/developers/docs/topics/opcodes-and-status-codes#http
                            */
                            Thread.sleep(3000);
                            continue;
                        }

                        //file too large
                        if (responseCode == 404) {
                            System.err.println("File not found: HTTP code 404");
                            JOptionPane.showMessageDialog(null,
                                    "Nie można ukończyć pobierania. Nie odnaleziono pliku " + fileToDl.getName() + "\nSzczegóły błędu: HTTP code 404", "Błąd", JOptionPane.ERROR_MESSAGE);
                            downloadResult = new Result(false, responseCode, "File not found");
                        }

                        if (responseCode == 200 || responseCode == 201) {
                            success = true;
                            downloadResult = new Result(true, responseCode, "");

                            out = new File(downloadDir + fileToDl.getName()); // zamiana na ścieżkę do pliku
                            System.out.println("download output: " + downloadDir + fileToDl.getName());

                            // Zapis pliku na dysku
                            try (FileOutputStream fileOutputStream = new FileOutputStream(out)) {
                                fileOutputStream.write(response.body().bytes());
                                System.out.println("Pobrano plik: " + out);

                                String downloadedPartHash = FileHashCalculator.getFileHash(out);
                                if (downloadedPartHash.equals(fileToDl.getSha256Hash())) {
                                    //progressGUI.incrementProgress();
                                } else {
                                    //todo: zrobić 3 opcje - nie, ponów próbę, tak (ponów można zrobić przez success = false)
                                    int dialogResult = JOptionPane.showConfirmDialog(null,
                                            "Suma kontrola pliku " + fileToDl.getName() + " jest niepoprawna. \nCzy mimo to chcesz kontynuować?", "Błąd", JOptionPane.YES_NO_OPTION);
                                    if (dialogResult == JOptionPane.YES_OPTION) {
                                        continue;
                                    } else {
                                        //progressGUI.dispose();
                                        //forceClose(true);
                                        downloadResult = new Result(false, responseCode, "Invalid file hash");
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            continue;
                        }

                        //nieznany błąd pomimo prób pobierania
                        System.err.println("Wystąpił nieznany błąd HTTP: " + responseCode);
                        downloadResult = new Result(false, responseCode, "Received unsupported http response");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (downloadResult == null) {
                    throw new RuntimeException("Download result is null");
                }

                return downloadResult;
            }
        });
    }
}
