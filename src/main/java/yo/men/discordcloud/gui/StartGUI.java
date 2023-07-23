package yo.men.discordcloud.gui;

import yo.men.discordcloud.Main;
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

    private JButton downloadButton;
    private JButton uploadButton;

    public StartGUI() {
        // Ustawienia okna
        setTitle("Discord Cloud");
        setSize(300, 150);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Utworzenie przycisków
        downloadButton = new JButton("Download");
        uploadButton = new JButton("Upload");

        // Ustawienie większej czcionki dla napisów na przyciskach
        Font largerFont = new Font(downloadButton.getFont().getName(), Font.PLAIN, 18);
        downloadButton.setFont(largerFont);
        uploadButton.setFont(largerFont);

        // Dodanie akcji do przycisków
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File fileToOpen = openFileSelectionGUI("*.json");
                if (fileToOpen != null) {
                    try (WebHookManager fm = new WebHookManager(Main.MAX_FILE_SIZE)) {
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
                if (fileToUpload != null) {
                    try (WebHookManager fm = new WebHookManager(settings.getWebhookUrl(), Main.MAX_FILE_SIZE);) {
                        fm.sendFile(fileToUpload);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(StartGUI.this,
                                "Wystąpił błąd nieoczekiwany błąd. \nSzczegóły błędu: \n" + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        // Ustawienie układu kontenera na GridLayout z jednym wierszem i dwiema kolumnami
        Container container = getContentPane();
        container.setLayout(new GridLayout(0, 1)); // 0 oznacza dowolną ilość wierszy, 1 kolumnę

        // Dodanie przycisków do kontenera
        container.add(downloadButton);
        container.add(uploadButton);
    }

    private File openFileSelectionGUI(String extensionFilter) {
        File toRet = null;
        FileDialog fd = new FileDialog(StartGUI.this, "Choose file to download", FileDialog.LOAD);
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
