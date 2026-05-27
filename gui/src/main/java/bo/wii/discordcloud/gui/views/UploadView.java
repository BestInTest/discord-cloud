package bo.wii.discordcloud.gui.views;

import bo.wii.discordcloud.core.DiscordCloudCore;
import bo.wii.discordcloud.core.services.ThumbnailService;
import bo.wii.discordcloud.core.services.upload.UploadBotTask;
import bo.wii.discordcloud.core.services.upload.UploadTask;
import bo.wii.discordcloud.core.services.upload.UploadProgressCallback;
import bo.wii.discordcloud.core.utils.FileHelper;
import bo.wii.discordcloud.gui.Configuration;
import bo.wii.discordcloud.gui.DiscordCloudGui;
import bo.wii.discordcloud.gui.Launcher;
import bo.wii.discordcloud.gui.dialogs.ThumbnailConfigDialog;
import bo.wii.discordcloud.gui.utils.Resources;
import bo.wii.discordcloud.gui.utils.SimpleAlerts;
import bo.wii.discordcloud.thumbnail.api.ThumbnailResolution;
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
import java.util.LinkedHashMap;
import java.util.Optional;

public class UploadView extends VBox {

    private enum UploadMode { WEBHOOK, BOT }

    private RadioButton webhookModeRadio;
    private RadioButton botModeRadio;
    private ComboBox<String> profileSelector;
    private Label channelWarningLabel;
    private Label fileLabel;
    private Label fileSizeLabel;
    private Label statusLabel;
    private Label progressPercentLabel;
    private ProgressBar progressBar;
    private Button chooseFileButton;
    private Button uploadButton;
    private Button stopButton;
    private Button backButton;
    private VBox fileInfoCard;
    private VBox progressCard;
    private VBox dropZone;
    private VBox profileCard;
    private HBox actionButtons;

    private UploadMode currentMode = UploadMode.WEBHOOK;
    private Thread uploadThread;
    private UploadTask uploadTask;
    private UploadBotTask uploadBotTask;
    private File selectedFile = null;
    private ThumbnailResolution selectedThumbnailResolution = null;
    private int selectedThumbnailQuality = ThumbnailService.DEFAULT_QUALITY;

    public UploadView() {
        super(0);
        setFillWidth(true);
        buildUI();
    }

    private void buildUI() {
        ImageView uploadIcon = Resources.loadImageView("upload.png");
        uploadIcon.setFitHeight(16);

        // Header
        Label header = new Label("Upload", uploadIcon);
        header.getStyleClass().add("view-header");
        //Label subheader = new Label("Upload files to Discord Cloud");
        //subheader.getStyleClass().add("view-subheader");
        //VBox headerBox = new VBox(4, header, subheader);
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


    private VBox buildProfileCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label title = new Label("Upload Method");
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

        // Channel ID warning for bot mode
        channelWarningLabel = new Label("Make sure channel-id is configured for this bot profile");
        channelWarningLabel.setStyle("-fx-text-fill: #faa61a; -fx-font-size: 11px;");
        channelWarningLabel.setVisible(false);
        channelWarningLabel.setManaged(false);

        // Mode change handlers
        webhookModeRadio.setOnAction(e -> {
            currentMode = UploadMode.WEBHOOK;
            populateProfileSelector();
        });
        botModeRadio.setOnAction(e -> {
            currentMode = UploadMode.BOT;
            populateProfileSelector();
        });

        // Profile change handler
        profileSelector.setOnAction(e -> updateChannelWarning());

        populateProfileSelector();

        card.getChildren().addAll(title, modeRow, profileRow, channelWarningLabel);
        return card;
    }

    private void populateProfileSelector() {
        Configuration conf = Launcher.getConfiguration();
        profileSelector.getItems().clear();

        if (currentMode == UploadMode.WEBHOOK) {
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
        updateChannelWarning();
    }

    private void updateChannelWarning() {
        if (currentMode != UploadMode.BOT) {
            channelWarningLabel.setVisible(false);
            channelWarningLabel.setManaged(false);
            return;
        }
        String profileName = profileSelector.getValue();
        if (profileName == null || profileName.isEmpty()) {
            channelWarningLabel.setVisible(false);
            channelWarningLabel.setManaged(false);
            return;
        }
        Configuration conf = Launcher.getConfiguration();
        Configuration.BotProfile bp = conf.getBot(profileName);
        boolean missing = bp == null || bp.getChannelId() == null || bp.getChannelId().isEmpty();
        channelWarningLabel.setVisible(missing);
        channelWarningLabel.setManaged(missing);
    }

    private VBox buildDropZone() {
        VBox zone = new VBox(8);
        zone.getStyleClass().add("drop-zone");
        zone.setAlignment(Pos.CENTER);
        zone.setMinHeight(120);

        ImageView iconImg = Resources.loadImageView("file_open.png");
        Label icon = new Label();
        icon.setGraphic(iconImg);
        icon.getStyleClass().add("drop-zone-icon");

        Label label = new Label("Drag & drop file here or click to browse");
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
                loadFile(db.getFiles().get(0));
                success = true;
            }
            zone.getStyleClass().remove("drop-zone-active");
            event.setDropCompleted(success);
            event.consume();
        });

        return zone;
    }


    private VBox buildFileInfoCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label title = new Label("Selected File");
        title.getStyleClass().add("card-title");

        fileLabel = new Label("--");
        fileLabel.getStyleClass().add("info-label-value");

        fileSizeLabel = new Label("--");
        fileSizeLabel.getStyleClass().add("info-label-key");

        card.getChildren().addAll(title, fileLabel, fileSizeLabel);
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

        HBox statsRow = new HBox(20);
        statsRow.setAlignment(Pos.CENTER_LEFT);

        progressPercentLabel = new Label("0%");
        progressPercentLabel.getStyleClass().add("info-label-value");

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        statsRow.getChildren().add(progressPercentLabel);

        stopButton = new Button("Cancel Upload");
        stopButton.getStyleClass().add("btn-danger");
        stopButton.setOnAction(e -> stopUploadProcess());

        backButton = new Button("Back");
        backButton.getStyleClass().add("btn-secondary");
        backButton.setVisible(false);
        backButton.setManaged(false);
        backButton.setOnAction(e -> resetToInitialState());

        HBox buttonRow = new HBox(12, stopButton, backButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, progressBar, statsRow, statusLabel, buttonRow);
        return card;
    }


    // Buttons

    private HBox buildActionButtons() {
        uploadButton = new Button("Upload");
        uploadButton.getStyleClass().add("btn-success");
        uploadButton.setOnAction(e -> startUploadProcess());

        HBox box = new HBox(12, uploadButton);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // File handling

    private void handleFileSelection() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select file to upload");
        File file = fc.showOpenDialog(DiscordCloudGui.getPrimaryStage());
        if (file != null) {
            loadFile(file);
        }
    }

    private void loadFile(File file) {
        selectedFile = file;
        fileLabel.setText(file.getName());
        fileSizeLabel.setText("Size: " + FileHelper.formatFileSize(file.length()));
        fileInfoCard.setVisible(true);
        fileInfoCard.setManaged(true);
        dropZone.setMinHeight(60);
    }


    // Uploading

    private void startUploadProcess() {
        if (selectedFile == null) {
            SimpleAlerts.showWarning("Please select a file first!");
            return;
        }
        if (!selectedFile.exists()) {
            SimpleAlerts.showError("Selected file does not exist!");
            return;
        }

        String profileName = profileSelector.getValue();
        if (profileName == null || profileName.isEmpty()) {
            SimpleAlerts.showWarning("Please select a profile!");
            return;
        }

        // Check thumbnail support
        String extension = FileHelper.getFileExtension(selectedFile);
        boolean supportsThumbnails = DiscordCloudCore.thumbnailService.findGeneratorFor(extension).isPresent();

        if (supportsThumbnails) {
            // Show thumbnail configuration dialog
            showThumbnailConfigDialog();
        } else {
            // Start upload without thumbnail configuration
            selectedThumbnailResolution = null;
            executeUpload();
        }
    }

    private void showThumbnailConfigDialog() {
        ThumbnailConfigDialog dialog = new ThumbnailConfigDialog(selectedFile.getName(), (resolution, quality)
                // Generate preview thumbnail with specified resolution and quality
                -> DiscordCloudCore.thumbnailService.generateThumbnailBytes(selectedFile, resolution, quality)
        );

        Optional<ThumbnailConfigDialog.ThumbnailConfig> result = dialog.showAndWait();
        if (result.isPresent() && dialog.isConfirmed()) {
            ThumbnailConfigDialog.ThumbnailConfig config = result.get();
            selectedThumbnailResolution = config.getResolution();
            selectedThumbnailQuality = config.getQuality();
            executeUpload();
        }
    }

    private void executeUpload() {
        // Hide setup UI, show progress
        profileCard.setVisible(false);
        profileCard.setManaged(false);
        dropZone.setVisible(false);
        dropZone.setManaged(false);
        fileInfoCard.setVisible(false);
        fileInfoCard.setManaged(false);
        actionButtons.setVisible(false);
        actionButtons.setManaged(false);

        // Show progress
        progressCard.setVisible(true);
        progressCard.setManaged(true);
        progressBar.setProgress(0);
        progressPercentLabel.setText("0%");
        updateStatus("Uploading...", null);

        // Always show stop button and hide back button
        stopButton.setVisible(true);
        stopButton.setManaged(true);
        backButton.setVisible(false);
        backButton.setManaged(false);

        Configuration conf = Launcher.getConfiguration();
        String profileName = profileSelector.getValue();

        UploadProgressCallback callback = new UploadProgressCallback() {
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
            public void onError(String message) {
                Platform.runLater(() -> {
                    SimpleAlerts.showError(message);
                    updateStatus("Error: " + message, "status-error");
                    setUploadingState(false);
                    showBackButton();
                });
            }

            @Override
            public void onComplete(String structureFile) {
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    progressPercentLabel.setText("100%");
                    updateStatus("Upload complete: " + structureFile, "status-success");
                    SimpleAlerts.showInfo("Upload complete!\nStructure file: " + structureFile);
                    setUploadingState(false);
                    showBackButton();
                });
            }
        };

        if (currentMode == UploadMode.WEBHOOK) {
            String webhookUrl = conf.getWebhookUrl(profileName);
            uploadTask = new UploadTask(selectedFile, webhookUrl, DiscordCloudCore.CHUNK_FILE_SIZE, callback);
            // Set thumbnail options if configured
            if (selectedThumbnailResolution != null) {
                uploadTask.setThumbnailResolution(selectedThumbnailResolution);
                uploadTask.setThumbnailQuality(selectedThumbnailQuality);
            }
            uploadThread = new Thread(() -> {
                uploadTask.execute();
            });
        } else {
            Configuration.BotProfile bp = conf.getBot(profileName);
            if (bp == null) {
                SimpleAlerts.showError("Bot profile not found: " + profileName);
                return;
            }
            uploadBotTask = new UploadBotTask(selectedFile, bp.getToken(), bp.getChannelId(),
                    DiscordCloudCore.CHUNK_FILE_SIZE, callback);
            if (selectedThumbnailResolution != null) {
                uploadBotTask.setThumbnailResolution(selectedThumbnailResolution);
                uploadBotTask.setThumbnailQuality(selectedThumbnailQuality);
            }
            uploadThread = new Thread(() -> {
                uploadBotTask.execute();
            });
        }

        uploadThread.setDaemon(true);
        uploadThread.start();

        // Add hook to stop upload when application closes
        setUploadingState(true);
        DiscordCloudGui.getPrimaryStage().setOnCloseRequest(e -> stopUploadProcess());
    }

    private void stopUploadProcess() {
        if (uploadTask != null) uploadTask.stop();
        if (uploadBotTask != null) uploadBotTask.stop();
        if (uploadThread != null && uploadThread.isAlive()) {
            uploadThread.interrupt();
            uploadThread = null;
        }
        resetToInitialState();
        updateStatus("Upload stopped", "status-warning");
    }

    private void resetToInitialState() {
        // Show setup UI again
        profileCard.setVisible(true);
        profileCard.setManaged(true);
        dropZone.setVisible(true);
        dropZone.setManaged(true);
        if (selectedFile != null) {
            fileInfoCard.setVisible(true);
            fileInfoCard.setManaged(true);
        }
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

        setUploadingState(false);
    }

    private void showBackButton() {
        stopButton.setVisible(false);
        stopButton.setManaged(false);
        backButton.setVisible(true);
        backButton.setManaged(true);
    }

    private void setUploadingState(boolean uploading) {
        uploadButton.setDisable(uploading);
        chooseFileButton.setDisable(uploading);
        profileSelector.setDisable(uploading);
        webhookModeRadio.setDisable(uploading);
        botModeRadio.setDisable(uploading);
        DiscordCloudGui.disableNavigation(uploading);
    }

    private void updateStatus(String text, String styleClass) {
        Platform.runLater(() -> {
            statusLabel.setText(text);
            statusLabel.getStyleClass().removeAll("status-success", "status-error", "status-warning", "status-label");
            statusLabel.getStyleClass().add(styleClass != null ? styleClass : "status-label");
        });
    }
}
