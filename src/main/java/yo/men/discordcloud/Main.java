package yo.men.discordcloud;

import com.google.gson.Gson;
import yo.men.discordcloud.gui.FileExplorerGUI;
import yo.men.discordcloud.gui.Settings;
import yo.men.discordcloud.gui.SettingsGUI;
import yo.men.discordcloud.gui.StartGUI;
import yo.men.discordcloud.utils.FileHelper;

import javax.swing.*;
import java.awt.*;
import java.io.*;


public class Main {

    public static final int MAX_FILE_SIZE = 25 * 1024 * 1023; // X MB (nie może być 1024^2, ponieważ jest error HTTP 413)
    public static final String STORAGE_DIR = "storage/";
    private static Settings settings;
    private static FileExplorerGUI explorerGUI;
    private static StartGUI startGUI;

    public static void main(String[] args) {
        //todo 2: sprawdzać czy plik juz istnieje

        loadSettings(); // settings będzie nullem jeżeli plik ustawień nie będzie istniał

        if (settings != null && settings.isClearCache()) {
            System.out.println("Czyszczenie cache");
            File cache = new File(".temp/");
            FileHelper.deleteDirectory(cache);
        }

        showStartGUI();
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

    public static void showStartGUI() {
        if (startGUI != null) {
            startGUI.setVisible(false);
        }
        SwingUtilities.invokeLater(() -> {
            startGUI = new StartGUI();
            startGUI.setVisible(true);
        });
    }

    public static void showSettingsGUI(Frame parent, boolean isModal) throws IOException {
        new SettingsGUI(parent, isModal);
    }

    public static Settings getSettings() {
        return settings;
    }

    public static FileExplorerGUI getExplorerGUI() {
        return explorerGUI;
    }

    public static void loadSettings() {
        File settingsFile = new File("settings.json");

        try (Reader reader = new FileReader(settingsFile)) {
            Gson gson = new Gson();
            Settings settings = gson.fromJson(reader, Settings.class);
            if (settings != null) {
                Main.settings = settings;
                return;
            }
        } catch (FileNotFoundException e) {
            Main.settings = null;
            return;
        } catch (IOException e) {
            e.printStackTrace();
        }
        Main.settings = null;
    }

}