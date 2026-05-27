package bo.wii.discordcloud.gui;

import bo.wii.discordcloud.gui.utils.Resources;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import bo.wii.discordcloud.gui.views.DeleteView;
import bo.wii.discordcloud.gui.views.DownloadView;
import bo.wii.discordcloud.gui.views.SettingsView;
import bo.wii.discordcloud.gui.views.UploadView;

import java.io.File;

public class DiscordCloudGui extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    private static Stage primaryStage;
    private StackPane contentArea;
    private static VBox sidebar;

    private ToggleButton downloadButton;
    private ToggleButton uploadButton;
    private ToggleButton deleteButton;
    private ToggleButton settingsButton;
    private ToggleGroup navGroup;

    @Override
    public void start(Stage primaryStage) {
        sidebar = createSidebar();

        contentArea = new StackPane();
        contentArea.setId("content-area");

        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(contentArea);
        root.setId("root");

        showWelcomeView();

        Scene scene = new Scene(root, 920, 620);
        scene.getStylesheets().add(Resources.getRequiredResource("/styles.css").toExternalForm());

        primaryStage.setTitle("Discord Cloud");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(750);
        primaryStage.setMinHeight(500);
        primaryStage.getIcons().add(Resources.loadAppIcon());
        primaryStage.show();
        DiscordCloudGui.primaryStage = primaryStage;

        // If .dscl / .json file was passed as argument (file association), open it automatically
        String pendingFile = Launcher.getPendingFile();
        if (pendingFile != null) {
            downloadButton.setSelected(true);
            DownloadView view = showDownloadView();
            view.loadFile(new File(pendingFile));
        }
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setId("sidebar");
        sidebar.setPadding(new Insets(20, 14, 20, 14));
        sidebar.setMinWidth(185);
        sidebar.setPrefWidth(185);
        sidebar.setMaxWidth(185);

        // header
        ImageView iconView = new ImageView(Resources.loadAppIcon());
        iconView.setFitHeight(48);
        iconView.setPreserveRatio(true);
        iconView.setSmooth(true);

        Label appTitle = new Label("Discord Cloud");
        appTitle.setId("app-title");

        Label versionLabel = new Label("v" + Version.getVersion());
        versionLabel.setId("version-label");

        VBox header = new VBox(2, iconView, appTitle, versionLabel);
        header.setPadding(new Insets(0, 0, 20, 4));

        // Separator
        Separator sep = new Separator();
        sep.getStyleClass().add("nav-separator");

        //Navigation
        navGroup = new ToggleGroup();

        ImageView downloadIcon = Resources.loadImageView("download.png");
        downloadIcon.setFitHeight(16);
        ImageView uploadIcon = Resources.loadImageView("upload.png");
        uploadIcon.setFitHeight(16);
        ImageView deleteIcon = Resources.loadImageView("close.png");
        deleteIcon.setFitHeight(16);
        ImageView settingsIcon = Resources.loadImageView("settings.png");
        settingsIcon.setFitHeight(16);

        downloadButton = createNavButton("Download", downloadIcon);
        uploadButton = createNavButton("Upload", uploadIcon);
        deleteButton = createNavButton("Delete", deleteIcon);
        settingsButton = createNavButton("Settings", settingsIcon);

        downloadButton.setToggleGroup(navGroup);
        uploadButton.setToggleGroup(navGroup);
        deleteButton.setToggleGroup(navGroup);
        settingsButton.setToggleGroup(navGroup);

        // Add action handlers to toggle views
        downloadButton.setOnAction(e -> {
            if (downloadButton.isSelected()) showDownloadView();
            else downloadButton.setSelected(true);
        });
        uploadButton.setOnAction(e -> {
            if (uploadButton.isSelected()) showUploadView();
            else uploadButton.setSelected(true);
        });
        deleteButton.setOnAction(e -> {
            if (deleteButton.isSelected()) showDeleteView();
            else deleteButton.setSelected(true);
        });
        settingsButton.setOnAction(e -> {
            if (settingsButton.isSelected()) showSettingsView();
            else settingsButton.setSelected(true);
        });

        VBox navBox = new VBox(4, downloadButton, uploadButton, deleteButton, settingsButton);
        navBox.setPadding(new Insets(16, 0, 0, 0));

        // Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Footer
        Label footerLink = new Label("dev.000-000.pl \ngithub.com/BestInTest");
        footerLink.setId("footer-link");

        sidebar.getChildren().addAll(header, sep, navBox, spacer, footerLink);
        return sidebar;
    }

    private ToggleButton createNavButton(String text, ImageView icon) {
        ToggleButton btn = new ToggleButton(text, icon);
        btn.getStyleClass().add("nav-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        return btn;
    }

    private DownloadView showDownloadView() {
        contentArea.getChildren().clear();
        DownloadView view = new DownloadView();
        contentArea.getChildren().add(view);
        return view;
    }

    private void showUploadView() {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(new UploadView());
    }

    private void showDeleteView() {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(new DeleteView());
    }

    private void showSettingsView() {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(new SettingsView());
    }

    private void showWelcomeView() {
        contentArea.getChildren().clear();

        VBox welcome = new VBox(12);
        welcome.setAlignment(Pos.CENTER);
        welcome.setMaxWidth(420);

        Label title = new Label("Discord Cloud");
        title.setId("welcome-title");

        Label subtitle = new Label("Upload and download files via Discord");
        subtitle.setId("welcome-subtitle");

        Label hint = new Label("Select an option from the sidebar to get started");
        hint.setId("welcome-subtitle");
        hint.setStyle("-fx-font-size: 12px; -fx-padding: 12 0 0 0;");

        welcome.getChildren().addAll(title, subtitle, hint);
        contentArea.getChildren().add(welcome);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Disables sidebar navigation during long-running tasks.
     */
    public static void disableNavigation(boolean disable) {
        if (sidebar != null) {
            sidebar.setDisable(disable);
        }
    }
}