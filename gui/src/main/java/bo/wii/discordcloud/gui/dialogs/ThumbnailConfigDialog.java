package bo.wii.discordcloud.gui.dialogs;

import bo.wii.discordcloud.thumbnail.api.ThumbnailResolution;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;

import java.io.ByteArrayInputStream;
import java.net.URL;

public class ThumbnailConfigDialog extends Dialog<ThumbnailConfigDialog.ThumbnailConfig> {

    private boolean confirmed = false;
    private ComboBox<ResolutionOption> resolutionComboBox;
    private Slider qualitySlider;
    private Label qualityLabel;
    private ImageView thumbnailPreview;
    private Label previewStatusLabel;
    private byte[] currentThumbnailData;

    public static class ThumbnailConfig {
        private final ThumbnailResolution resolution;
        private final int quality;

        public ThumbnailConfig(ThumbnailResolution resolution, int quality) {
            this.resolution = resolution;
            this.quality = quality;
        }

        public ThumbnailResolution getResolution() {
            return resolution;
        }

        public int getQuality() {
            return quality;
        }
    }

    private static class ResolutionOption {
        private final String name;
        private final ThumbnailResolution resolution;

        public ResolutionOption(String name, ThumbnailResolution resolution) {
            this.name = name;
            this.resolution = resolution;
        }

        @Override
        public String toString() {
            return name + " (" + resolution.toString() + ")";
        }

        public ThumbnailResolution getResolution() {
            return resolution;
        }
    }

    public ThumbnailConfigDialog(String fileName, ThumbnailPreviewGenerator previewGenerator) {
        setTitle("Thumbnail Configuration");
        initModality(Modality.APPLICATION_MODAL);

        getDialogPane().setPrefSize(700, 700);
        getDialogPane().setMinSize(600, 600);

        // Load CSS stylesheet
        try {
            URL cssUrl = getClass().getResource("/styles.css");
            if (cssUrl != null) {
                getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            System.err.println("Failed to load stylesheet: " + e.getMessage());
        }

        getDialogPane().getStyleClass().add("file-info-dialog");

        // Create content
        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        content.setAlignment(Pos.TOP_CENTER);
        content.getStyleClass().add("file-info-content");

        // Header
        Label headerLabel = new Label("Configure Thumbnail Generation");
        headerLabel.getStyleClass().add("dialog-custom-header");
        headerLabel.setWrapText(true);
        headerLabel.setMaxWidth(Double.MAX_VALUE);
        headerLabel.setAlignment(Pos.CENTER);
        content.getChildren().add(headerLabel);

        // File info
        Label fileLabel = new Label("File: " + fileName);
        fileLabel.getStyleClass().add("info-header");
        content.getChildren().add(fileLabel);

        Separator separator1 = new Separator();
        separator1.getStyleClass().add("info-separator");
        content.getChildren().add(separator1);

        // Configuration section
        Label configHeader = new Label("Thumbnail Settings");
        configHeader.getStyleClass().add("info-header");
        content.getChildren().add(configHeader);

        GridPane configGrid = new GridPane();
        configGrid.setHgap(15);
        configGrid.setVgap(12);
        configGrid.setPadding(new Insets(10, 15, 10, 15));
        configGrid.getStyleClass().add("info-grid");

        // Resolution selector
        Label resolutionLabel = new Label("Resolution:");
        resolutionLabel.getStyleClass().add("info-label");
        
        resolutionComboBox = new ComboBox<>();
        resolutionComboBox.getItems().addAll(
            new ResolutionOption("SD 360p", ThumbnailResolution.SD_360P),
            new ResolutionOption("SD 480p", ThumbnailResolution.SD_480P),
            new ResolutionOption("HD 720p", ThumbnailResolution.HD_720P),
            new ResolutionOption("HD 1080p", ThumbnailResolution.HD_1080P)
        );
        resolutionComboBox.setValue(resolutionComboBox.getItems().get(2)); // Default: 720p
        resolutionComboBox.setPrefWidth(250);
        resolutionComboBox.getStyleClass().add("thumbnail-combo-box");

        configGrid.add(resolutionLabel, 0, 0);
        configGrid.add(resolutionComboBox, 1, 0);

        // Quality slider
        Label qualityLabelTitle = new Label("Quality:");
        qualityLabelTitle.getStyleClass().add("info-label");
        
        HBox qualityBox = new HBox(10);
        qualityBox.setAlignment(Pos.CENTER_LEFT);
        
        qualitySlider = new Slider(50, 100, 75);
        qualitySlider.setShowTickLabels(true);
        qualitySlider.setShowTickMarks(true);
        qualitySlider.setMajorTickUnit(10);
        qualitySlider.setMinorTickCount(1);
        qualitySlider.setPrefWidth(180);
        qualitySlider.getStyleClass().add("thumbnail-slider");
        
        qualityLabel = new Label("75%");
        qualityLabel.getStyleClass().add("info-value");
        qualityLabel.setMinWidth(50);
        
        qualitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            qualityLabel.setText(newVal.intValue() + "%");
        });
        
        qualityBox.getChildren().addAll(qualitySlider, qualityLabel);
        
        configGrid.add(qualityLabelTitle, 0, 1);
        configGrid.add(qualityBox, 1, 1);

        content.getChildren().add(configGrid);

        // Preview button
        Button generatePreviewButton = new Button("Generate Preview");
        generatePreviewButton.getStyleClass().add("dialog-download-button");
        generatePreviewButton.setOnAction(e -> generatePreview(previewGenerator));
        content.getChildren().add(generatePreviewButton);

        Separator separator2 = new Separator();
        separator2.getStyleClass().add("info-separator");
        content.getChildren().add(separator2);

        // Preview section
        Label previewHeader = new Label("Thumbnail Preview");
        previewHeader.getStyleClass().add("info-header");
        content.getChildren().add(previewHeader);

        VBox previewBox = new VBox(10);
        previewBox.setAlignment(Pos.CENTER);
        previewBox.getStyleClass().add("thumbnail-container");
        previewBox.setPrefHeight(300);
        previewBox.setMinHeight(200);

        previewStatusLabel = new Label("Click 'Generate Preview' to see thumbnail");
        previewStatusLabel.getStyleClass().add("no-thumbnail-label");

        thumbnailPreview = new ImageView();
        thumbnailPreview.setPreserveRatio(true);
        thumbnailPreview.setFitWidth(400);
        thumbnailPreview.setFitHeight(250);
        thumbnailPreview.getStyleClass().add("thumbnail-preview");
        thumbnailPreview.setVisible(false);
        thumbnailPreview.setCursor(Cursor.HAND);
        
        Tooltip clickTooltip = new Tooltip("Click to view full size");
        Tooltip.install(thumbnailPreview, clickTooltip);
        thumbnailPreview.setOnMouseClicked(event -> {
            if (thumbnailPreview.getImage() != null) {
                showEnlargedImage(thumbnailPreview.getImage());
            }
        });

        previewBox.getChildren().addAll(previewStatusLabel, thumbnailPreview);
        content.getChildren().add(previewBox);

        // Wrap in ScrollPane
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("file-info-scroll-pane");
        scrollPane.setPrefHeight(600);
        scrollPane.setMaxHeight(600);

        getDialogPane().setContent(scrollPane);

        // Buttons
        ButtonType uploadButtonType = new ButtonType("Upload with Thumbnail", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(uploadButtonType, cancelButtonType);

        Button uploadButton = (Button) getDialogPane().lookupButton(uploadButtonType);
        Button cancelButton = (Button) getDialogPane().lookupButton(cancelButtonType);

        if (uploadButton != null) {
            uploadButton.getStyleClass().add("dialog-download-button");
        }
        if (cancelButton != null) {
            cancelButton.getStyleClass().add("dialog-cancel-button");
        }

        // Handle result
        setResultConverter(buttonType -> {
            if (buttonType == uploadButtonType) {
                confirmed = true;
                ResolutionOption selected = resolutionComboBox.getValue();
                int quality = (int) qualitySlider.getValue();
                return new ThumbnailConfig(selected.getResolution(), quality);
            }
            return null;
        });
    }

    private void generatePreview(ThumbnailPreviewGenerator generator) {
        previewStatusLabel.setText("Generating preview...");
        previewStatusLabel.getStyleClass().removeAll("no-thumbnail-label", "error-label");
        previewStatusLabel.getStyleClass().add("info-value");
        thumbnailPreview.setVisible(false);

        // Run in background thread
        Thread generationThread = new Thread(() -> {
            try {
                ResolutionOption selected = resolutionComboBox.getValue();
                int quality = (int) qualitySlider.getValue();
                
                byte[] thumbnailData = generator.generate(selected.getResolution(), quality);
                currentThumbnailData = thumbnailData;

                javafx.application.Platform.runLater(() -> {
                    try {
                        Image image = new Image(new ByteArrayInputStream(thumbnailData));
                        thumbnailPreview.setImage(image);
                        thumbnailPreview.setVisible(true);
                        previewStatusLabel.setText("Preview generated successfully");
                        previewStatusLabel.getStyleClass().remove("error-label");
                        previewStatusLabel.getStyleClass().add("status-valid");
                    } catch (Exception e) {
                        showPreviewError("Failed to load preview image");
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    showPreviewError("Error: " + e.getMessage());
                });
            }
        });
        generationThread.setDaemon(true);
        generationThread.start();
    }

    private void showPreviewError(String message) {
        previewStatusLabel.setText("(!) " + message);
        previewStatusLabel.getStyleClass().removeAll("no-thumbnail-label", "info-value", "status-valid");
        previewStatusLabel.getStyleClass().add("error-label");
        thumbnailPreview.setVisible(false);
    }

    private void showEnlargedImage(Image image) {
        Dialog<Void> imageDialog = new Dialog<>();
        imageDialog.setTitle("Thumbnail Preview");
        imageDialog.initModality(Modality.APPLICATION_MODAL);

        try {
            URL cssUrl = getClass().getResource("/styles.css");
            if (cssUrl != null) {
                imageDialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            System.err.println("Failed to load stylesheet: " + e.getMessage());
        }

        imageDialog.getDialogPane().getStyleClass().add("enlarged-image-dialog");

        ImageView enlargedView = new ImageView(image);
        enlargedView.setPreserveRatio(true);
        enlargedView.setFitWidth(Math.min(image.getWidth(), 1200));
        enlargedView.setFitHeight(Math.min(image.getHeight(), 800));
        enlargedView.getStyleClass().add("enlarged-thumbnail");

        StackPane imagePane = new StackPane(enlargedView);
        imagePane.setPadding(new Insets(20));
        imagePane.getStyleClass().add("enlarged-image-pane");

        enlargedView.setCursor(Cursor.HAND);
        Tooltip closeTooltip = new Tooltip("Click to close");
        Tooltip.install(enlargedView, closeTooltip);
        enlargedView.setOnMouseClicked(event -> imageDialog.close());

        imageDialog.getDialogPane().setContent(imagePane);

        ButtonType closeButtonType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        imageDialog.getDialogPane().getButtonTypes().add(closeButtonType);

        Button closeButton = (Button) imageDialog.getDialogPane().lookupButton(closeButtonType);
        if (closeButton != null) {
            closeButton.getStyleClass().add("dialog-cancel-button");
        }

        imageDialog.showAndWait();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Interface for generating thumbnail previews
     */
    public interface ThumbnailPreviewGenerator {
        byte[] generate(ThumbnailResolution resolution, int quality) throws Exception;
    }
}

