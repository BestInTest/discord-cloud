package bo.wii.discordcloud.gui;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Version {
    private static final String VERSION;

    static {
        String version = "unknown";
        try (InputStream input = Version.class.getClassLoader().getResourceAsStream("version.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);
                version = prop.getProperty("version", "unknown");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        VERSION = version;
    }

    public static String getVersion() {
        return VERSION;
    }

    public static String getUploaderJarName() {
        return "uploader-" + VERSION + ".jar";
    }

    public static String getServerJarName() {
        return "server-" + VERSION + ".jar";
    }

    public static String getDownloaderJarName() {
        return "downloader-" + VERSION + ".jar";
    }
}
