package yo.men.discordcloud;

import com.google.gson.Gson;
import yo.men.discordcloud.gui.Settings;
import yo.men.discordcloud.gui.SettingsGUI;
import yo.men.discordcloud.gui.StartGUI;
import yo.men.discordcloud.utils.FileHelper;

import javax.swing.*;
import java.awt.*;
import java.io.*;


public class Main {

    public static final int CHUNK_FILE_SIZE = 25 * 1024 * 1023; // X MB (nie może być 1024^2, ponieważ jest error HTTP 413)
    private static Settings settings;
    private static StartGUI startGUI;

    public static void main(String[] args) {

        loadSettings(); // settings będzie nullem jeżeli plik ustawień nie będzie istniał

        if (settings != null && settings.isClearCache()) {
            System.out.println("Usuwanie plików tymczasowych");
            File cache = new File(".temp/");
            FileHelper.deleteDirectory(cache);
        }

        showStartGUI();
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

    public static StartGUI getStartGUI() {
        return startGUI;
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