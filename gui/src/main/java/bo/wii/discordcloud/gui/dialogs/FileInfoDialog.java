package bo.wii.discordcloud.gui.dialogs;

import bo.wii.discordcloud.core.structure.FileStruct;
import bo.wii.discordcloud.core.utils.FileHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Base64;

public class FileInfoDialog extends Dialog<ButtonType> {

    public FileInfoDialog(FileStruct fileStruct) {

        setTitle("File Information");

        // Set dialog as modal
        initModality(Modality.APPLICATION_MODAL);

        // Set dialog size
        getDialogPane().setPrefSize(700, 650);
        getDialogPane().setMinSize(600, 500);

        // Load CSS for the dialog
        try {
            URL cssUrl = getClass().getResource("/styles.css");
            if (cssUrl != null) {
                getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
            } else {
                System.err.println("Failed to load stylesheet: styles.css not found");
            }
        } catch (Exception e) {
            System.err.println("Failed to load stylesheet: " + e.getMessage());
        }

        // Apply CSS styling to dialog pane
        getDialogPane().getStyleClass().add("file-info-dialog");

        // Create the content container
        VBox content = new VBox(12);
        content.setPadding(new Insets(15));
        content.setAlignment(Pos.TOP_CENTER);
        content.getStyleClass().add("file-info-content");

        // Add separator after header
        Separator headerSeparator = new Separator();
        headerSeparator.getStyleClass().add("info-separator");
        content.getChildren().add(headerSeparator);

        // Thumbnail preview
        if (fileStruct.getThumbnailBase64() != null && !fileStruct.getThumbnailBase64().isEmpty()) {
            try {
                byte[] imageBytes = Base64.getDecoder().decode(fileStruct.getThumbnailBase64());
                Image thumbnailImage = new Image(new ByteArrayInputStream(imageBytes));
                ImageView thumbnailView = new ImageView(thumbnailImage);

                // Set max dimensions for thumbnail
                thumbnailView.setPreserveRatio(true);
                thumbnailView.setFitWidth(400);
                thumbnailView.setFitHeight(250);
                thumbnailView.getStyleClass().add("thumbnail-preview");

                // Make thumbnail clickable
                thumbnailView.setCursor(Cursor.HAND);
                Tooltip clickTooltip = new Tooltip("Click to view full size");
                Tooltip.install(thumbnailView, clickTooltip);

                // Add click handler to show enlarged image
                thumbnailView.setOnMouseClicked(event -> showEnlargedImage(thumbnailImage));

                VBox thumbnailBox = new VBox(8);
                thumbnailBox.setAlignment(Pos.CENTER);
                thumbnailBox.getStyleClass().add("thumbnail-container");
                //thumbnailBox.getChildren().addAll(thumbnailLabel, thumbnailView);
                thumbnailBox.getChildren().add(thumbnailView);

                content.getChildren().add(thumbnailBox);
            } catch (Exception e) {
                Label errorLabel = new Label("Failed to load thumbnail preview");
                errorLabel.getStyleClass().add("error-label");
                content.getChildren().add(errorLabel);
            }
        } else {
            Label noThumbnailLabel = new Label("No thumbnail available");
            noThumbnailLabel.getStyleClass().add("no-thumbnail-label");
            VBox placeholderBox = new VBox();
            placeholderBox.setAlignment(Pos.CENTER);
            placeholderBox.setPadding(new Insets(15));
            placeholderBox.getChildren().add(noThumbnailLabel);
            content.getChildren().add(placeholderBox);
        }

        // Separator
        Separator separator = new Separator();
        separator.getStyleClass().add("info-separator");
        content.getChildren().add(separator);

        // File information section
        Label infoHeaderLabel = new Label("File Details");
        infoHeaderLabel.getStyleClass().add("info-header");
        content.getChildren().add(infoHeaderLabel);

        // File information grid
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(15);
        infoGrid.setVgap(12);
        infoGrid.setPadding(new Insets(10, 15, 10, 15));
        infoGrid.getStyleClass().add("info-grid");

        int row = 0;

        // File name
        Label fileNameLabel = new Label("File Name:");
        fileNameLabel.getStyleClass().add("info-label");
        Label fileNameValue = new Label(fileStruct.getOriginalFileName());
        fileNameValue.getStyleClass().add("info-value");
        fileNameValue.setWrapText(true);
        fileNameValue.setMaxWidth(350);
        infoGrid.add(fileNameLabel, 0, row);
        infoGrid.add(fileNameValue, 1, row++);

        // Upload date
        Label fileUploadDateLabel = new Label("Upload Date:");
        fileUploadDateLabel.getStyleClass().add("info-label");
        Label fileUploadDateValue = new Label(FileHelper.formatTimestamp(fileStruct.getUploadTimestamp()));
        fileUploadDateValue.getStyleClass().add("info-value");
        infoGrid.add(fileUploadDateLabel, 0, row);
        infoGrid.add(fileUploadDateValue, 1, row++);

        // File size
        Label fileSizeLabel = new Label("File Size:");
        fileSizeLabel.getStyleClass().add("info-label");
        Label fileSizeValue = new Label(FileHelper.formatFileSize(fileStruct.getFileSize()));
        fileSizeValue.getStyleClass().add("info-value");
        infoGrid.add(fileSizeLabel, 0, row);
        infoGrid.add(fileSizeValue, 1, row++);

        // Number of parts
        Label partsLabel = new Label("Number of Parts:");
        partsLabel.getStyleClass().add("info-label");
        Label partsValue = new Label(String.valueOf(fileStruct.getParts().size()));
        partsValue.getStyleClass().add("info-value");
        infoGrid.add(partsLabel, 0, row);
        infoGrid.add(partsValue, 1, row++);

        // Part size
        Label partSizeLabel = new Label("Single Part Size:");
        partSizeLabel.getStyleClass().add("info-label");
        Label partSizeValue = new Label(FileHelper.formatFileSize(fileStruct.getSinglePartSize()));
        partSizeValue.getStyleClass().add("info-value");
        infoGrid.add(partSizeLabel, 0, row);
        infoGrid.add(partSizeValue, 1, row++);

        // File version
        Label versionLabel = new Label("File Version:");
        versionLabel.getStyleClass().add("info-label");
        Label versionValue = new Label(String.valueOf(fileStruct.getFileVersion()));
        versionValue.getStyleClass().add("info-value");
        infoGrid.add(versionLabel, 0, row);
        infoGrid.add(versionValue, 1, row++);

        // SHA256 Hash (truncated for display)
        Label hashLabel = new Label("SHA256 Hash:");
        hashLabel.getStyleClass().add("info-label");
        String truncatedHash = fileStruct.getSha256Hash().substring(0, 32) + "...";
        Label hashValue = new Label(truncatedHash);
        hashValue.getStyleClass().add("info-value");
        hashValue.setTooltip(new Tooltip(fileStruct.getSha256Hash()));
        infoGrid.add(hashLabel, 0, row);
        infoGrid.add(hashValue, 1, row++);

        // Expired status
        /*
        //TODO: Re-add in some time (when some other TODO will be done...)
        Label expiredLabel = new Label("Status:");
        expiredLabel.getStyleClass().add("info-label");
        String status = fileStruct.isExpired() ? "Links Expired" : "Valid";
        Label expiredValue = new Label(status);
        expiredValue.getStyleClass().add("info-value");
        if (fileStruct.isExpired()) {
            expiredValue.getStyleClass().add("status-expired");
        } else {
            expiredValue.getStyleClass().add("status-valid");
        }
        infoGrid.add(expiredLabel, 0, row);
        infoGrid.add(expiredValue, 1, row);
        */

        content.getChildren().add(infoGrid);

        // Wrap content in ScrollPane to prevent overlapping with buttons
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("file-info-scroll-pane");
        scrollPane.setPrefHeight(550);
        scrollPane.setMaxHeight(550);

        getDialogPane().setContent(scrollPane);

        // Add close button only
        ButtonType closeButtonType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().add(closeButtonType);

        // Style button
        Button closeButton = (Button) getDialogPane().lookupButton(closeButtonType);
        if (closeButton != null) {
            closeButton.getStyleClass().add("dialog-cancel-button");
        }
    }

    /**
     * Shows an enlarged version of the thumbnail in a new dialog
     */
    private void showEnlargedImage(Image image) {
        Dialog<Void> imageDialog = new Dialog<>();
        imageDialog.setTitle("Thumbnail Preview");
        imageDialog.initModality(Modality.APPLICATION_MODAL);

        // Load CSS stylesheet
        try {
            URL cssUrl = getClass().getResource("/styles.css");
            if (cssUrl != null) {
                imageDialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            System.err.println("Failed to load stylesheet: " + e.getMessage());
        }

        // Apply styling
        imageDialog.getDialogPane().getStyleClass().add("enlarged-image-dialog");

        // Create ImageView with full size
        ImageView enlargedView = new ImageView(image);
        enlargedView.setPreserveRatio(true);

        // Set max size based on screen dimensions
        enlargedView.setFitWidth(Math.min(image.getWidth(), 1200));
        enlargedView.setFitHeight(Math.min(image.getHeight(), 800));
        enlargedView.getStyleClass().add("enlarged-thumbnail");

        // Wrap in StackPane for centering
        StackPane imagePane = new StackPane(enlargedView);
        imagePane.setPadding(new Insets(20));
        imagePane.getStyleClass().add("enlarged-image-pane");

        // Make the image clickable to close
        enlargedView.setCursor(Cursor.HAND);
        Tooltip closeTooltip = new Tooltip("Click to close");
        Tooltip.install(enlargedView, closeTooltip);
        enlargedView.setOnMouseClicked(event -> imageDialog.close());

        imageDialog.getDialogPane().setContent(imagePane);

        // Add close button
        ButtonType closeButtonType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        imageDialog.getDialogPane().getButtonTypes().add(closeButtonType);

        // Style close button
        Button closeButton = (Button) imageDialog.getDialogPane().lookupButton(closeButtonType);
        if (closeButton != null) {
            closeButton.getStyleClass().add("dialog-cancel-button");
        }

        imageDialog.showAndWait();
    }
}
