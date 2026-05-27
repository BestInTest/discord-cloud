package bo.wii.discordcloud.gui.utils;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;

public class Resources {

    public static URL getRequiredResource(String path) {
        URL resource = Resources.class.getResource(path);
        if (resource == null) {
            throw new IllegalStateException("Missing resource: " + path);
        }
        return resource;
    }

    public static Image loadAppIcon() {
        return new Image(getRequiredResource("/icon.png").toExternalForm());
    }

    public static ImageView loadImageView(String img) {
        Image i = new Image(getRequiredResource("/" + img).toExternalForm());
        ImageView iv = new ImageView(i);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        return iv;
    }
}
