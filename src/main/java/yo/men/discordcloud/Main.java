package yo.men.discordcloud;

import com.google.gson.Gson;
import yo.men.discordcloud.gui.FileExplorerGUI;
import yo.men.discordcloud.gui.Settings;
import yo.men.discordcloud.gui.SettingsGUI;
import yo.men.discordcloud.utils.FileHelper;

import javax.swing.*;
import java.io.*;


public class Main {

    public static final int MAX_FILE_SIZE = 25 * 1024 * 1023; // X MB (nie może być 2024^2, poniważ jest error HTTP 413)
    public static final String STORAGE_DIR = "storage/";
    private static Settings settings;
    private static FileExplorerGUI explorerGUI;

    public static void main(String[] args) {

        /*
        todo 1:
          Zrobić żeby FileExplorerGUI otwierał folder "storage" i tam można by było zarządzać plikami
          i folderami oraz wysyłać nowe pliki je.
          Ewentualnie zmienić aby było menu do wybrania czy wysyłać albo pobierać
        */
        //todo 2: sprawdzać czy plik juz istnieje

        File settingsFile = new File("settings.json");

        if (settingsFile.exists()) {
            settings = loadSettings();
            // Jeśli plik settings.json istnieje, otwórz program bez GUI ustawień
            System.out.println("Plik ustawień istnieje.");
            if (settings.isClearCache()) {
                System.out.println("Czyszczenie cache");
                File cache = new File(".temp/");
                FileHelper.deleteDirectory(cache);
            }
            showFileExplorerGUI("storage/");
        } else {
            // Jeśli plik settings.json nie istnieje, wyświetl GUI ustawień
            System.out.println("Plik ustawień nie istnieje.");
            showSettingsGUI(true);
        }

    }

    public static void showFileExplorerGUI(String path) {
        if (explorerGUI != null) {
            explorerGUI.setVisible(false);
        }
        SwingUtilities.invokeLater(() -> {
            explorerGUI = new FileExplorerGUI();
            explorerGUI.setVisible(true);
            explorerGUI.displayFilesAndDirectories(path);
        });
    }

    public static void showSettingsGUI(boolean startAfterExit) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new SettingsGUI(startAfterExit);
            }
        });
    }

    public static Settings getSettings() {
        return settings;
    }

    public static FileExplorerGUI getExplorerGUI() {
        return explorerGUI;
    }

    private static Settings loadSettings() {
        File settingsFile = new File("settings.json");

        try (Reader reader = new FileReader(settingsFile)) {
            Gson gson = new Gson();
            Settings settings = gson.fromJson(reader, Settings.class);
            if (settings != null) {
                return settings;
            }
        } catch (FileNotFoundException e) { //Struktura jeszcze nie istnieje, - wysyłanie pierwszego kawałka
            //Tworzenie nowej struktury pliku
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}