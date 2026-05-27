package bo.wii.discordcloud.gui.views;

import bo.wii.discordcloud.core.client.LinkRefresher;
import bo.wii.discordcloud.core.client.LinkRefresherFactory;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.core.services.download.DownloadTask;
import bo.wii.discordcloud.core.services.download.DownloadProgressCallback;
import bo.wii.discordcloud.gui.Configuration;
import bo.wii.discordcloud.gui.DiscordCloudGui;
import bo.wii.discordcloud.gui.Launcher;
import bo.wii.discordcloud.gui.dialogs.FileInfoDialog;
import bo.wii.discordcloud.gui.utils.Resources;
import bo.wii.discordcloud.gui.utils.SimpleAlerts;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;

public class DownloadView extends VBox {

    private enum DownloadMode { WEBHOOK, BOT }

    private RadioButton webhookModeRadio;
    private RadioButton botModeRadio;
    private ComboBox<String> profileSelector;
    private DownloadMode currentMode = DownloadMode.WEBHOOK;

    private Label fileNameLabel;
    private Label fileSizeLabel;
    private Label filePartsLabel;
    private Label statusLabel;
    private Label speedLabel;
    private Label progressPercentLabel;
    private Label downloadedSizeLabel;
    private Label etaLabel;
    private ProgressBar progressBar;
    private Button chooseFileButton;
    private Button downloadButton;
    private Button stopButton;
    private Button backButton;
    private VBox fileInfoCard;
    private VBox progressCard;
    private VBox dropZone;
    private VBox profileCard;
    private HBox actionButtons;

    private Thread downloadThread;
    private DownloadTask downloadTask;
    private File selectedFile = null;
    private FileStruct currentStructure = null;

    public DownloadView() {
        super(0);
        setFillWidth(true);
        buildUI();
    }

    private void buildUI() {
        ImageView downloadIcon = Resources.loadImageView("download.png");
        downloadIcon.setFitHeight(16);

        // Header
        Label header = new Label("Download", downloadIcon);
        header.getStyleClass().add("view-header");
        VBox headerBox = new VBox(4, header);

        // Profile card
        profileCard = buildProfileCard();

        // Drop zone
        dropZone = buildDropZone();

        // File info card (hidden)
        fileInfoCard = buildFileInfoCard();
        fileInfoCard.setVisible(false);
        fileInfoCard.setManaged(false);

        // Progress card (hidden)
        progressCard = buildProgressCard();
        progressCard.setVisible(false);
        progressCard.setManaged(false);

        // Actions
        actionButtons = buildActionButtons();

        VBox content = new VBox(16, headerBox, profileCard, dropZone, fileInfoCard, progressCard, actionButtons);
        content.setFillWidth(true);
        content.setPadding(new Insets(24));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().add(scrollPane);
    }

    // Profile Card

    private VBox buildProfileCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label title = new Label("Download Method");
        title.getStyleClass().add("card-title");

        // Mode selector
        ToggleGroup modeGroup = new ToggleGroup();
        webhookModeRadio = new RadioButton("Webhook");
        botModeRadio = new RadioButton("Bot");
        webhookModeRadio.setToggleGroup(modeGroup);
        botModeRadio.setToggleGroup(modeGroup);
        webhookModeRadio.setSelected(true);

        HBox modeRow = new HBox(20, webhookModeRadio, botModeRadio);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        // Profile selector
        HBox profileRow = new HBox(10);
        profileRow.setAlignment(Pos.CENTER_LEFT);
        Label profLabel = new Label("Profile:");
        profLabel.getStyleClass().add("info-label-key");
        profLabel.setMinWidth(80);

        profileSelector = new ComboBox<>();
        profileSelector.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(profileSelector, Priority.ALWAYS);

        profileRow.getChildren().addAll(profLabel, profileSelector);

        // Mode change handlers
        webhookModeRadio.setOnAction(e -> {
            currentMode = DownloadMode.WEBHOOK;
            populateProfileSelector();
        });
        botModeRadio.setOnAction(e -> {
            currentMode = DownloadMode.BOT;
            populateProfileSelector();
        });

        populateProfileSelector();

        card.getChildren().addAll(title, modeRow, profileRow);
        return card;
    }

    private void populateProfileSelector() {
        Configuration conf = Launcher.getConfiguration();
        profileSelector.getItems().clear();

        if (currentMode == DownloadMode.WEBHOOK) {
            LinkedHashMap<String, String> webhooks = conf.getWebhooks();
            profileSelector.getItems().addAll(webhooks.keySet());
            String selected = conf.getSelectedWebhook();
            if (webhooks.containsKey(selected)) {
                profileSelector.setValue(selected);
            } else if (!webhooks.isEmpty()) {
                profileSelector.setValue(webhooks.keySet().iterator().next());
            }
        } else {
            LinkedHashMap<String, Configuration.BotProfile> bots = conf.getBots();
            profileSelector.getItems().addAll(bots.keySet());
            if (!bots.isEmpty()) {
                profileSelector.setValue(bots.keySet().iterator().next());
            }
        }
    }

    // Drop Zone

    private VBox buildDropZone() {
        VBox zone = new VBox(8);
        zone.getStyleClass().add("drop-zone");
        zone.setAlignment(Pos.CENTER);
        zone.setMinHeight(120);

        ImageView iconImg = Resources.loadImageView("file_open.png");
        Label icon = new Label();
        icon.setGraphic(iconImg);
        icon.getStyleClass().add("drop-zone-icon");

        Label label = new Label("Drag & drop .dscl or .json file here or click to browse");
        label.getStyleClass().add("drop-zone-label");

        chooseFileButton = new Button("Browse Files");
        chooseFileButton.getStyleClass().addAll("btn-secondary", "btn-small");
        chooseFileButton.setOnAction(e -> handleFileSelection());

        zone.getChildren().addAll(icon, label, chooseFileButton);

        // Drag & Drop
        zone.setOnDragOver(event -> {
            if (event.getGestureSource() != zone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                if (!zone.getStyleClass().contains("drop-zone-active")) {
                    zone.getStyleClass().add("drop-zone-active");
                }
            }
            event.consume();
        });

        zone.setOnDragExited(event -> {
            zone.getStyleClass().remove("drop-zone-active");
            event.consume();
        });

        zone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                File file = db.getFiles().get(0);
                if (file.getName().endsWith(".dscl") || file.getName().endsWith(".json")) {
                    loadFile(file);
                    success = true;
                } else {
                    SimpleAlerts.showWarning("Please select a .dscl or .json structure file.");
                }
            }
            zone.getStyleClass().remove("drop-zone-active");
            event.setDropCompleted(success);
            event.consume();
        });

        return zone;
    }

    // File Info Card

    private VBox buildFileInfoCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label title = new Label("File Information");
        title.getStyleClass().add("card-title");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(8);

        fileNameLabel = createInfoValue("--");
        fileSizeLabel = createInfoValue("--");
        filePartsLabel = createInfoValue("--");

        grid.add(createInfoKey("Name:"), 0, 0);
        grid.add(fileNameLabel, 1, 0);
        grid.add(createInfoKey("Size:"), 0, 1);
        grid.add(fileSizeLabel, 1, 1);
        grid.add(createInfoKey("Parts:"), 0, 2);
        grid.add(filePartsLabel, 1, 2);

        Button detailsButton = new Button("View Details");
        detailsButton.getStyleClass().addAll("btn-secondary", "btn-small");
        detailsButton.setOnAction(e -> {
            if (currentStructure != null) {
                new FileInfoDialog(currentStructure).showAndWait();
            }
        });

        card.getChildren().addAll(title, grid, detailsButton);
        return card;
    }

    // Progress Card

    private VBox buildProgressCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label title = new Label("Progress");
        title.getStyleClass().add("card-title");

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setMinHeight(8);

        // Row 1: percent + speed
        HBox statsRow1 = new HBox(20);
        statsRow1.setAlignment(Pos.CENTER_LEFT);

        progressPercentLabel = new Label("0%");
        progressPercentLabel.getStyleClass().add("info-label-value");

        speedLabel = new Label("Speed: --");
        speedLabel.getStyleClass().add("info-label-key");

        statsRow1.getChildren().addAll(progressPercentLabel, speedLabel);

        // Row 2: downloaded size + ETA
        HBox statsRow2 = new HBox(20);
        statsRow2.setAlignment(Pos.CENTER_LEFT);

        downloadedSizeLabel = new Label("Downloaded: --");
        downloadedSizeLabel.getStyleClass().add("info-label-key");

        etaLabel = new Label("ETA: --");
        etaLabel.getStyleClass().add("info-label-key");

        statsRow2.getChildren().addAll(downloadedSizeLabel, etaLabel);

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        stopButton = new Button("Cancel Download");
        stopButton.getStyleClass().add("btn-danger");
        stopButton.setOnAction(e -> stopDownloadProcess());

        backButton = new Button("Back");
        backButton.getStyleClass().add("btn-secondary");
        backButton.setVisible(false);
        backButton.setManaged(false);
        backButton.setOnAction(e -> resetToInitialState());

        HBox buttonRow = new HBox(12, stopButton, backButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, progressBar, statsRow1, statsRow2, statusLabel, buttonRow);
        return card;
    }

    // Action Buttons

    private HBox buildActionButtons() {
        downloadButton = new Button("Download");
        downloadButton.getStyleClass().add("btn-success");
        downloadButton.setOnAction(e -> startDownloadProcess());

        HBox box = new HBox(12, downloadButton);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // Helpers

    private Label createInfoKey(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("info-label-key");
        l.setMinWidth(60);
        return l;
    }

    private Label createInfoValue(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("info-label-value");
        return l;
    }

    // File loading

    private void handleFileSelection() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Discord Cloud File");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Discord Cloud Files", "*.dscl", "*.json"),
                new FileChooser.ExtensionFilter("Discord Cloud Files (new)", "*.dscl"),
                new FileChooser.ExtensionFilter("Discord Cloud Files (legacy)", "*.json"));
        File file = fc.showOpenDialog(DiscordCloudGui.getPrimaryStage());
        if (file != null) {
            loadFile(file);
        }
    }

    public void loadFile(File file) {
        try {
            FileStruct structure = FileHelper.loadStructureFile(file);
            if (structure != null && structure.isValid()) {
                selectedFile = file;
                currentStructure = structure;

                fileNameLabel.setText(structure.getOriginalFileName());
                fileSizeLabel.setText(FileHelper.formatFileSize(structure.getFileSize()));
                filePartsLabel.setText(String.valueOf(structure.getParts().size()));

                fileInfoCard.setVisible(true);
                fileInfoCard.setManaged(true);

                dropZone.setMinHeight(60);

                String statusMsg = "File loaded: " + structure.getOriginalFileName();
                if (structure.isExpired()) {
                    statusMsg += " Links may be expired";
                }
                updateStatus(statusMsg, structure.isExpired() ? "status-warning" : "status-success");
            } else {
                SimpleAlerts.showWarning("Invalid file structure.");
                updateStatus("Invalid file structure", "status-error");
            }
        } catch (IOException ex) {
            SimpleAlerts.showError("Error loading file: " + ex.getMessage());
            updateStatus("Error: " + ex.getMessage(), "status-error");
        }
    }

    // Download process

    private long downloadStartTime;

    private void startDownloadProcess() {
        if (selectedFile == null || currentStructure == null) {
            SimpleAlerts.showWarning("Please select a file first!");
            return;
        }

        Configuration conf = Launcher.getConfiguration();
        String profileName = profileSelector.getValue();

        if (profileName == null || profileName.isEmpty()) {
            SimpleAlerts.showWarning("Please select a profile first!");
            return;
        }

        LinkRefresher linkRefresher;
        try {
            if (currentMode == DownloadMode.WEBHOOK) {
                String webhookUrl = conf.getWebhookUrl(profileName);
                linkRefresher = LinkRefresherFactory.createWebhookRefresher(webhookUrl);
            } else {
                Configuration.BotProfile bp = conf.getBot(profileName);
                if (bp == null) {
                    SimpleAlerts.showError("Bot profile not found: " + profileName);
                    return;
                }
                String channelId = currentStructure.getEffectiveChannelId();
                linkRefresher = LinkRefresherFactory.createBotTokenRefresher(bp.getToken(), channelId);
            }
        } catch (IllegalArgumentException e) {
            SimpleAlerts.showError("Configuration error: " + e.getMessage());
            return;
        }

        // Hide setup UI, show progress
        profileCard.setVisible(false);
        profileCard.setManaged(false);
        dropZone.setVisible(false);
        dropZone.setManaged(false);
        actionButtons.setVisible(false);
        actionButtons.setManaged(false);

        progressCard.setVisible(true);
        progressCard.setManaged(true);
        progressBar.setProgress(0);
        progressPercentLabel.setText("0%");
        speedLabel.setText("Speed: --");
        downloadedSizeLabel.setText("Downloaded: --");
        etaLabel.setText("ETA: --");
        updateStatus("Downloading...", null);

        // Always show stop button and hide back button
        stopButton.setVisible(true);
        stopButton.setManaged(true);
        backButton.setVisible(false);
        backButton.setManaged(false);

        downloadStartTime = System.currentTimeMillis();

        downloadTask = new DownloadTask(currentStructure, linkRefresher, new DownloadProgressCallback() {
            @Override
            public void onLog(String message) {
                Platform.runLater(() -> updateStatus(message, null));
            }

            @Override
            public void onProgress(int current, int total) {
                Platform.runLater(() -> {
                    double p = (double) current / total;
                    progressBar.setProgress(p);
                    progressPercentLabel.setText(String.format("%.1f%%", p * 100));
                });
            }

            @Override
            public void onProgressWithSpeed(int current, int total, long downloadedBytes, long totalBytes, double speedBps) {
                Platform.runLater(() -> {
                    double p = (double) downloadedBytes / totalBytes;
                    progressBar.setProgress(p);
                    progressPercentLabel.setText(String.format("%.1f%%", p * 100));
                    speedLabel.setText("Speed: " + FileHelper.formatSpeed(speedBps));
                    downloadedSizeLabel.setText("Downloaded: " + FileHelper.formatFileSize(downloadedBytes)
                            + " / " + FileHelper.formatFileSize(totalBytes));

                    // Calculate ETA
                    if (speedBps > 0) {
                        long remainingBytes = totalBytes - downloadedBytes;
                        long etaSeconds = (long) (remainingBytes / speedBps);
                        etaLabel.setText("ETA: " + FileHelper.formatEta(etaSeconds));
                    } else {
                        etaLabel.setText("ETA: --");
                    }
                });
            }

            @Override
            public void onError(String message) {
                Platform.runLater(() -> {
                    SimpleAlerts.showError(message);
                    updateStatus("Error: " + message, "status-error");
                    setDownloadingState(false);
                    showBackButton();
                });
            }

            @Override
            public void onComplete(String outputFile) {
                Platform.runLater(() -> {
                    long elapsed = System.currentTimeMillis() - downloadStartTime;
                    progressBar.setProgress(1.0);
                    progressPercentLabel.setText("100%");
                    speedLabel.setText("Speed: --");
                    etaLabel.setText("Time: " + FileHelper.formatEta(elapsed / 1000));
                    downloadedSizeLabel.setText("Downloaded: " + FileHelper.formatFileSize(currentStructure.getFileSize())
                            + " / " + FileHelper.formatFileSize(currentStructure.getFileSize()));
                    updateStatus("Download complete: " + outputFile, "status-success");
                    SimpleAlerts.showInfo("Download complete!\nFile saved to: " + outputFile);
                    setDownloadingState(false);
                    showBackButton();
                });
            }
        }, conf.isPrefetchEnabled(), conf.isCheckPartHash());

        downloadThread = new Thread(() -> {
            downloadTask.execute();
        });
        downloadThread.setDaemon(true);
        downloadThread.start();

        // Add hook to stop download when application closes
        setDownloadingState(true);
        DiscordCloudGui.getPrimaryStage().setOnCloseRequest(e -> stopDownloadProcess());
    }

    private void stopDownloadProcess() {
        if (downloadTask != null) downloadTask.stop();
        if (downloadThread != null && downloadThread.isAlive()) {
            downloadThread.interrupt();
            downloadThread = null;
        }
        resetToInitialState();
        updateStatus("Download stopped", "status-warning");
    }

    private void resetToInitialState() {
        // Show setup UI again
        profileCard.setVisible(true);
        profileCard.setManaged(true);
        dropZone.setVisible(true);
        dropZone.setManaged(true);
        actionButtons.setVisible(true);
        actionButtons.setManaged(true);

        // Hide progress
        progressCard.setVisible(false);
        progressCard.setManaged(false);

        // Hide back button and show stop button
        backButton.setVisible(false);
        backButton.setManaged(false);
        stopButton.setVisible(true);
        stopButton.setManaged(true);

        progressBar.setProgress(0);
        progressPercentLabel.setText("0%");
        speedLabel.setText("Speed: --");
        downloadedSizeLabel.setText("Downloaded: --");
        etaLabel.setText("ETA: --");

        setDownloadingState(false);
    }

    private void showBackButton() {
        stopButton.setVisible(false);
        stopButton.setManaged(false);
        backButton.setVisible(true);
        backButton.setManaged(true);
    }

    private void setDownloadingState(boolean downloading) {
        downloadButton.setDisable(downloading);
        chooseFileButton.setDisable(downloading);
        profileSelector.setDisable(downloading);
        webhookModeRadio.setDisable(downloading);
        botModeRadio.setDisable(downloading);
        DiscordCloudGui.disableNavigation(downloading);
    }

    private void updateStatus(String text, String styleClass) {
        Platform.runLater(() -> {
            statusLabel.setText(text);
            statusLabel.getStyleClass().removeAll("status-success", "status-error", "status-warning", "status-label");
            statusLabel.getStyleClass().add(styleClass != null ? styleClass : "status-label");
        });
    }
}
