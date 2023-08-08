package yo.men.discordcloud.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import yo.men.discordcloud.Main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileWriter;
import java.io.IOException;

public class SettingsGUI extends JDialog {
    private JCheckBox clearCacheCheckBox;
    private JTextField webhookUrlField;
    private JButton saveButton;

    public SettingsGUI(Frame parent, boolean isModal) {
        super(parent, isModal);
        setTitle("Ustawienia");
        setPreferredSize(new Dimension(400, 140));
        setLayout(new BorderLayout());

        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new GridLayout(2, 2, 10, 10));

        JLabel clearCacheLabel = new JLabel("Czyść cache przy włączeniu:");
        clearCacheCheckBox = new JCheckBox();

        JLabel webhookUrlLabel = new JLabel("URL webhooka:");
        webhookUrlField = new JTextField();

        Settings settings = Main.getSettings();
        if (settings != null) { // jeżeli plik setting.json istnieje, gui zostanie uzupełnione o aktualne ustawienia
            clearCacheCheckBox.setSelected(true);
            webhookUrlField.setText(settings.getWebhookUrl());
        }

        settingsPanel.add(clearCacheLabel);
        settingsPanel.add(clearCacheCheckBox);
        settingsPanel.add(webhookUrlLabel);
        settingsPanel.add(webhookUrlField);

        saveButton = new JButton("Zapisz");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    saveSettings();
                } catch (IOException ex) {
                    throw new RuntimeException();
                }
            }
        });

        add(settingsPanel, BorderLayout.CENTER);
        add(saveButton, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void saveSettings() throws IOException {
        String settingsFilePath = "settings.json";

        Settings settings = new Settings();
        settings.setClearCache(clearCacheCheckBox.isSelected());
        settings.setWebhookUrl(webhookUrlField.getText());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(settings);

        FileWriter fileWriter = new FileWriter(settingsFilePath);
        fileWriter.write(json);
        fileWriter.close();
        Main.loadSettings();

        JOptionPane.showMessageDialog(this, "Ustawienia zostały zapisane.", "Informacja", JOptionPane.INFORMATION_MESSAGE);
        dispose(); // Zamykanie ustawień
    }
}
