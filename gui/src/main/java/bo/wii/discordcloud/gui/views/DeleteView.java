package bo.wii.discordcloud.gui.views;

import bo.wii.discordcloud.core.services.delete.DeleteProgressCallback;
import bo.wii.discordcloud.core.services.delete.DeleteTask;
import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.utils.FileHelper;
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

public class DeleteView extends VBox {

    private enum DeleteMode { WEBHOOK, BOT }

    private RadioButton webhookModeRadio;
    private RadioButton botModeRadio;
    private ComboBox<String> profileSelector;
    private DeleteMode currentMode = DeleteMode.WEBHOOK;

    private Label fileNameLabel;
    private Label fileSizeLabel;
    private Label filePartsLabel;
    private Label statusLabel;
    private Label progressPercentLabel;
    private ProgressBar progressBar;
    private Button chooseFileButton;
    private Button deleteButton;
    private Button stopButton;
    private Button backButton;
    private VBox fileInfoCard;
    private VBox progressCard;
    private VBox dropZone;
    private VBox profileCard;
    private HBox actionButtons;

    private Thread deleteThread;
    private DeleteTask deleteTask;
    private File selectedFile = null;
    private FileStruct currentStructure = null;

    public DeleteView() {
        super(0);
        setFillWidth(true);
        buildUI();
    }

    private void buildUI() {
        ImageView deleteIcon = Resources.loadImageView("close.png");
        deleteIcon.setFitHeight(16);

        // Header
        Label header = new Label("Delete", deleteIcon);
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


    // Profile card

    private VBox buildProfileCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label title = new Label("Delete Method");
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
            currentMode = DeleteMode.WEBHOOK;
            populateProfileSelector();
        });
        botModeRadio.setOnAction(e -> {
            currentMode = DeleteMode.BOT;
            populateProfileSelector();
        });

        populateProfileSelector();

        card.getChildren().addAll(title, modeRow, profileRow);
        return card;
    }

    private void populateProfileSelector() {
        Configuration conf = Launcher.getConfiguration();
        profileSelector.getItems().clear();

        if (currentMode == DeleteMode.WEBHOOK) {
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

        statsRow.getChildren().add(progressPercentLabel);

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        stopButton = new Button("Cancel Deletion");
        stopButton.getStyleClass().add("btn-danger");
        stopButton.setOnAction(e -> stopDeleteProcess());

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


    private HBox buildActionButtons() {
        deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("btn-danger");
        deleteButton.setOnAction(e -> confirmAndStartDeleteProcess());

        HBox box = new HBox(12, deleteButton);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }



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

    private void loadFile(File file) {
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

                updateStatus("File loaded: " + structure.getOriginalFileName(), "status-success");
            } else {
                SimpleAlerts.showWarning("Invalid file structure.");
                updateStatus("Invalid file structure", "status-error");
            }
        } catch (IOException ex) {
            SimpleAlerts.showError("Error loading file: " + ex.getMessage());
            updateStatus("Error: " + ex.getMessage(), "status-error");
        }
    }

    // Delete process

    private void confirmAndStartDeleteProcess() {
        if (selectedFile == null || currentStructure == null) {
            SimpleAlerts.showWarning("Please select a file first!");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete all Discord messages?");
        confirm.setContentText("This will permanently delete all " + currentStructure.getParts().size()
                + " message(s) associated with \"" + currentStructure.getOriginalFileName()
                + "\".\n\nThis action cannot be undone!");
        confirm.initOwner(DiscordCloudGui.getPrimaryStage());
        confirm.getDialogPane().setMinWidth(420);
        confirm.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        confirm.getDialogPane().getStylesheets().add(
                Resources.getRequiredResource("/styles.css").toExternalForm());

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                startDeleteProcess();
            }
        });
    }

    private void startDeleteProcess() {
        Configuration conf = Launcher.getConfiguration();
        String profileName = profileSelector.getValue();

        if (profileName == null || profileName.isEmpty()) {
            SimpleAlerts.showWarning("Please select a profile first!");
            return;
        }

        String webhookUrl = null;
        String botToken = null;

        if (currentMode == DeleteMode.WEBHOOK) {
            webhookUrl = conf.getWebhookUrl(profileName);
        } else {
            Configuration.BotProfile bp = conf.getBot(profileName);
            if (bp == null) {
                SimpleAlerts.showError("Bot profile not found: " + profileName);
                return;
            }
            botToken = bp.getToken();
        }

        final String finalWebhookUrl = webhookUrl;
        final String finalBotToken = botToken;

        // Hide setup UI, show progress
        profileCard.setVisible(false);
        profileCard.setManaged(false);
        dropZone.setVisible(false);
        dropZone.setManaged(false);
        fileInfoCard.setVisible(false);
        fileInfoCard.setManaged(false);
        actionButtons.setVisible(false);
        actionButtons.setManaged(false);

        progressCard.setVisible(true);
        progressCard.setManaged(true);
        progressBar.setProgress(0);
        progressPercentLabel.setText("0%");

        // Make sure stop button is visible and back button is hidden
        stopButton.setVisible(true);
        stopButton.setManaged(true);
        backButton.setVisible(false);
        backButton.setManaged(false);

        updateStatus("Deleting...", null);

        deleteTask = new DeleteTask(currentStructure, finalWebhookUrl, finalBotToken, new DeleteProgressCallback() {
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
                    setDeletingState(false);
                    showBackButton();
                });
            }

            @Override
            public void onComplete(int deleted, int total) {
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    progressPercentLabel.setText("100%");
                    if (deleted == total) {
                        updateStatus("Deletion complete: " + deleted + "/" + total + " parts deleted", "status-success");
                        SimpleAlerts.showInfo("Deletion complete!\nDeleted " + deleted + " of " + total + " parts.");
                    } else {
                        updateStatus("Deletion finished: " + deleted + "/" + total + " parts deleted", "status-warning");
                        SimpleAlerts.showWarning("Deletion finished with issues.\nDeleted " + deleted + " of " + total + " parts.");
                    }
                    setDeletingState(false);
                    showBackButton();
                });
            }
        });

        deleteThread = new Thread(() -> deleteTask.execute());
        deleteThread.setDaemon(true);
        deleteThread.start();

        setDeletingState(true);
        DiscordCloudGui.getPrimaryStage().setOnCloseRequest(e -> stopDeleteProcess());
    }

    private void stopDeleteProcess() {
        if (deleteTask != null) deleteTask.stop();
        if (deleteThread != null && deleteThread.isAlive()) {
            deleteThread.interrupt();
            deleteThread = null;
        }
        resetToInitialState();
        updateStatus("Deletion cancelled", "status-warning");
    }

    private void resetToInitialState() {
        // Show setup UI again
        profileCard.setVisible(true);
        profileCard.setManaged(true);
        dropZone.setVisible(true);
        dropZone.setManaged(true);
        if (currentStructure != null) {
            fileInfoCard.setVisible(true);
            fileInfoCard.setManaged(true);
        }
        actionButtons.setVisible(true);
        actionButtons.setManaged(true);

        // Hide progress
        progressCard.setVisible(false);
        progressCard.setManaged(false);

        // Reset buttons
        stopButton.setVisible(true);
        stopButton.setManaged(true);
        backButton.setVisible(false);
        backButton.setManaged(false);

        progressBar.setProgress(0);
        progressPercentLabel.setText("0%");

        setDeletingState(false);
    }

    private void showBackButton() {
        stopButton.setVisible(false);
        stopButton.setManaged(false);
        backButton.setVisible(true);
        backButton.setManaged(true);
    }

    private void setDeletingState(boolean deleting) {
        deleteButton.setDisable(deleting);
        chooseFileButton.setDisable(deleting);
        profileSelector.setDisable(deleting);
        webhookModeRadio.setDisable(deleting);
        botModeRadio.setDisable(deleting);
        DiscordCloudGui.disableNavigation(deleting);
    }

    private void updateStatus(String text, String styleClass) {
        Platform.runLater(() -> {
            statusLabel.setText(text);
            statusLabel.getStyleClass().removeAll("status-success", "status-error", "status-warning", "status-label");
            statusLabel.getStyleClass().add(styleClass != null ? styleClass : "status-label");
        });
    }
}

