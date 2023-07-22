package yo.men.discordcloud.gui;

import yo.men.discordcloud.structure.DiscordFile;
import yo.men.discordcloud.utils.FileHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;

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
                FileDialog fd = new FileDialog(StartGUI.this, "Choose file to download", FileDialog.LOAD);
                fd.setDirectory("");
                fd.setFile("*.json"); // może zamiast tego zrobić FileNameFilter bo *może* to powodować wielokrotny wybór
                fd.setVisible(true);
                File[] selectedFiles = fd.getFiles();
                if (selectedFiles.length == 1) {
                    File selectedFile = selectedFiles[0];
                    if (selectedFile != null) {
                        try {
                            DiscordFile f = FileHelper.loadStructureFile(selectedFile);
                            //todo pobieranie
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            JOptionPane.showMessageDialog(StartGUI.this,
                                    "Wystąpił błąd podczs ładowania pliku. Upewnij się czy wybrałeś poprawny plik. \nSzczegóły błędu: \n" + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } else if (selectedFiles.length > 1) {
                    JOptionPane.showMessageDialog(StartGUI.this,
                            "Wybrano wiele plików. Proszę wybrać tylko jeden.", "Błąd", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        uploadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Tutaj dodaj kod obsługujący akcję po kliknięciu przycisku "Upload"
                JOptionPane.showMessageDialog(StartGUI.this,
                        "Wybrano opcję Upload.");
            }
        });

        // Ustawienie układu kontenera na GridLayout z jednym wierszem i dwiema kolumnami
        Container container = getContentPane();
        container.setLayout(new GridLayout(0, 1)); // 0 oznacza dowolną ilość wierszy, 1 kolumnę

        // Dodanie przycisków do kontenera
        container.add(downloadButton);
        container.add(uploadButton);
    }
}
