package yo.men.discordcloud.gui;

import yo.men.discordcloud.Main;
import yo.men.discordcloud.client.AttachmentRefresher;
import yo.men.discordcloud.structure.DiscordFileStruct;
import yo.men.discordcloud.structure.WebHookManager;
import yo.men.discordcloud.utils.FileHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

public class StartGUI extends JFrame {

    public StartGUI() {
        // Ustawienia okna
        setTitle("Discord Cloud");
        setSize(300, 200);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Utworzenie przycisków
        JButton downloadButton = new JButton("Download");
        JButton uploadButton = new JButton("Upload");
        JButton refreshButton = new JButton("Refresh links");
        JButton settingsButton = new JButton("Settings");

        // Ustawienie większej czcionki dla napisów na przyciskach
        Font largerFont = new Font(downloadButton.getFont().getName(), Font.PLAIN, 18);
        downloadButton.setFont(largerFont);
        uploadButton.setFont(largerFont);
        refreshButton.setFont(largerFont);
        settingsButton.setFont(largerFont);

        // Dodanie akcji do przycisków
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File fileToOpen = openFileSelectionGUI("*.json");
                if (fileToOpen != null) {
                    try {
                        WebHookManager fm = new WebHookManager(Main.CHUNK_FILE_SIZE);
                        DiscordFileStruct f = FileHelper.loadStructureFile(fileToOpen);
                        fm.downloadFile(f);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(StartGUI.this,
                                "Wystąpił błąd podczs ładowania pliku. Upewnij się czy wybrałeś poprawny plik. \nSzczegóły błędu: \n" + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        uploadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Settings settings = Main.getSettings();

                //Sprawdzanie ustawień
                if (settings == null) {
                    try {
                        Main.showSettingsGUI(StartGUI.this, true);
                        settings = Main.getSettings();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(StartGUI.this, "Wystąpił błąd podczas zapisywania ustawień. \nSzczegóły błędu:\n" + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                File fileToUpload = openFileSelectionGUI(null);
                DiscordFileStruct existingStruct = null;
                int chunkSize = Main.CHUNK_FILE_SIZE;
                if (fileToUpload != null) {

                    //sprawdzanie czy nie trzeba kontynuować wysyłania
                    try {
                        File existingFile = new File(fileToUpload.getName() + ".json");
                        if (existingFile.exists()) {
                            DiscordFileStruct loadedStruct = FileHelper.loadStructureFile(existingFile);
                            if (loadedStruct != null && loadedStruct.isValid()) {
                                //podmiana wartości, jeżeli załadowana struktura jest poprawna
                                existingStruct = loadedStruct;
                                chunkSize = loadedStruct.getSinglePartSize();
                            } else {
                                JOptionPane.showMessageDialog(StartGUI.this,
                                        "Znaleziono istniejący plik .json lecz nie można go poprawnie załadować. \nSzczegóły błędu: plik jest uszkodzony", "Błąd", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(StartGUI.this,
                                "Znaleziono istniejący plik .json lecz nie można go załadować. \nSzczegóły błędu: \n" + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    }

                    /*
                    W przypadku kontynuacji wysyłania plik może zostać wysłany na inny webhook,
                     lecz nie powinno to powodować problemu z późniejszym pobieraniem.
                     */
                    try {
                        WebHookManager fm = new WebHookManager(settings.getWebhookUrl(), chunkSize);
                        if (existingStruct == null) {
                            //Wysyłanie nowego pliku, jeżeli nie wykryto istniejącej struktury
                            fm.uploadFile(fileToUpload);
                        } else {
                            //Kontynuacja wysyłania, jeżeli wykryto istniejącą strukturę
                            fm.resumeUploading(fileToUpload, existingStruct);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(StartGUI.this,
                                "Wystąpił nieoczekiwany błąd. \nSzczegóły błędu: \n" + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Settings settings = Main.getSettings();

                //Sprawdzanie ustawień
                if (settings == null) {
                    try {
                        Main.showSettingsGUI(StartGUI.this, true);
                        settings = Main.getSettings();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(StartGUI.this, "Wystąpił błąd podczas zapisywania ustawień. \nSzczegóły błędu:\n" + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                File fileToOpen = openFileSelectionGUI("*.json");
                if (fileToOpen != null) {
                    try {
                        AttachmentRefresher refresher = new AttachmentRefresher(settings.getWebhookUrl());
                        refresher.refreshAttachments(fileToOpen);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(StartGUI.this,
                                "Wystąpił błąd podczs ładowania pliku. Upewnij się czy wybrałeś poprawny plik. \nSzczegóły błędu: \n" + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        settingsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Main.showSettingsGUI(StartGUI.this, true);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(StartGUI.this, "Wystąpił błąd podczas zapisywania ustawień. \nSzczegóły błędu:\n" + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Ustawienie układu kontenera na GridLayout z jednym wierszem i dwiema kolumnami
        Container container = getContentPane();
        container.setLayout(new GridLayout(0, 1)); // 0 oznacza dowolną ilość wierszy, 1 kolumnę

        // Dodanie przycisków do kontenera
        container.add(downloadButton);
        container.add(uploadButton);
        container.add(refreshButton);
        container.add(settingsButton);
    }

    private File openFileSelectionGUI(String extensionFilter) {
        File toRet = null;
        FileDialog fd = new FileDialog(StartGUI.this, "Choose file", FileDialog.LOAD);
        fd.setDirectory("");
        if (extensionFilter != null) {
            fd.setFile(extensionFilter); // może zamiast tego zrobić FileNameFilter bo *może* to powodować wielokrotny wybór
        }
        fd.setVisible(true);
        File[] selectedFiles = fd.getFiles();
        if (selectedFiles.length == 1) {
            File selectedFile = selectedFiles[0];
            if (selectedFile != null) {
                toRet = selectedFile;
            }
        } else if (selectedFiles.length > 1) {
            JOptionPane.showMessageDialog(StartGUI.this,
                    "Wybrano wiele plików. Proszę wybrać tylko jeden.", "Błąd", JOptionPane.ERROR_MESSAGE);
        }
        return toRet;
    }
}
