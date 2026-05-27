package bo.wii.discordcloud.gui.views;

import bo.wii.discordcloud.gui.Configuration;
import bo.wii.discordcloud.gui.Launcher;
import bo.wii.discordcloud.gui.utils.Resources;
import bo.wii.discordcloud.gui.utils.SimpleAlerts;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Optional;

public class SettingsView extends VBox {

    private VBox webhookList;
    private VBox botList;

    public SettingsView() {
        super(0);
        setFillWidth(true);
        buildUI();
    }

    private void buildUI() {
        ImageView settingsIcon = Resources.loadImageView("settings.png");
        settingsIcon.setFitHeight(16);

        // Header
        Label header = new Label("Settings", settingsIcon);
        header.getStyleClass().add("view-header");
        VBox headerBox = new VBox(4, header);

        // Sections
        VBox webhooksSection = buildWebhooksSection();
        VBox botsSection = buildBotsSection();
        VBox generalSection = buildGeneralSection();

        VBox content = new VBox(20, headerBox, webhooksSection, botsSection, generalSection);
        content.setFillWidth(true);
        content.setPadding(new Insets(24));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().add(scrollPane);
    }


    // Webhooks

    private VBox buildWebhooksSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");

        Label title = new Label("Webhooks");
        title.getStyleClass().add("card-title");

        Label desc = new Label("Configure webhook URLs for uploading and downloading files");
        desc.getStyleClass().add("card-subtitle");

        webhookList = new VBox(8);
        refreshWebhookList();

        Button addButton = new Button("+ Add Webhook");
        addButton.getStyleClass().addAll("btn-secondary", "btn-small");
        addButton.setOnAction(e -> showAddWebhookDialog());

        section.getChildren().addAll(title, desc, webhookList, addButton);
        return section;
    }

    private void refreshWebhookList() {
        webhookList.getChildren().clear();
        Configuration conf = Launcher.getConfiguration();
        LinkedHashMap<String, String> webhooks = conf.getWebhooks();
        String selectedWebhook = conf.getSelectedWebhook();

        if (webhooks.isEmpty()) {
            Label emptyLabel = new Label("No webhooks configured");
            emptyLabel.getStyleClass().add("card-subtitle");
            emptyLabel.setPadding(new Insets(8));
            webhookList.getChildren().add(emptyLabel);
            return;
        }

        for (var entry : webhooks.entrySet()) {
            String name = entry.getKey();
            String url = entry.getValue();
            boolean isActive = name.equals(selectedWebhook);

            HBox item = new HBox(12);
            item.setAlignment(Pos.CENTER_LEFT);
            item.getStyleClass().add("profile-item");
            if (isActive) {
                item.getStyleClass().add("profile-item-active");
            }

            VBox info = new VBox(2);
            HBox.setHgrow(info, Priority.ALWAYS);

            HBox nameRow = new HBox(8);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("profile-item-name");

            if (isActive) {
                Label activeTag = new Label("[active]");
                activeTag.setStyle("-fx-text-fill: #57f287; -fx-font-size: 11px; -fx-font-weight: bold;");
                nameRow.getChildren().addAll(nameLabel, activeTag);
            } else {
                nameRow.getChildren().add(nameLabel);
            }

            Label urlLabel = new Label(truncateUrl(url));
            urlLabel.getStyleClass().add("profile-item-detail");
            urlLabel.setTooltip(new Tooltip(url));

            info.getChildren().addAll(nameRow, urlLabel);

            // Actions
            ImageView selectImg = Resources.loadImageView("select.png");
            selectImg.setFitHeight(16);
            Button selectBtn = new Button("", selectImg);
            selectBtn.getStyleClass().add("btn-icon");
            selectBtn.setTooltip(new Tooltip("Set as active"));
            selectBtn.setDisable(isActive);
            selectBtn.setOnAction(e -> {
                conf.setSelectedWebhook(name);
                refreshWebhookList();
            });

            ImageView editImg = Resources.loadImageView("edit.png");
            editImg.setFitHeight(16);
            Button editBtn = new Button("", editImg);
            editBtn.getStyleClass().add("btn-icon");
            editBtn.setTooltip(new Tooltip("Edit"));
            editBtn.setOnAction(e -> showEditWebhookDialog(name, url));

            ImageView deleteImg = Resources.loadImageView("close.png");
            deleteImg.setFitHeight(16);
            Button deleteBtn = new Button("", deleteImg);
            deleteBtn.getStyleClass().add("btn-icon");
            deleteBtn.setTooltip(new Tooltip("Delete"));
            deleteBtn.setOnAction(e -> {
                if (webhooks.size() <= 1) {
                    SimpleAlerts.showWarning("Cannot delete the last webhook!");
                    return;
                }
                conf.removeWebhook(name);
                refreshWebhookList();
            });

            item.getChildren().addAll(info, selectBtn, editBtn, deleteBtn);
            webhookList.getChildren().add(item);
        }
    }

    private void showAddWebhookDialog() {
        Dialog<String[]> dialog = createWebhookDialog("Add Webhook", "", "");
        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(values -> {
            if (!values[0].isEmpty() && !values[1].isEmpty()) {
                Launcher.getConfiguration().addWebhook(values[0], values[1]);
                refreshWebhookList();
            }
        });
    }

    private void showEditWebhookDialog(String name, String url) {
        Dialog<String[]> dialog = createWebhookDialog("Edit Webhook", name, url);
        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(values -> {
            if (!values[1].isEmpty()) {
                Launcher.getConfiguration().updateWebhook(name, values[1]);
                refreshWebhookList();
            }
        });
    }

    private Dialog<String[]> createWebhookDialog(String dialogTitle, String existingName, String existingUrl) {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle(dialogTitle);
        dialog.setResizable(true);

        try {
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/styles.css").toExternalForm());
        } catch (Exception ignored) {}
        dialog.getDialogPane().getStyleClass().add("file-info-dialog");
        dialog.getDialogPane().setPrefWidth(500);
        dialog.getDialogPane().setMinWidth(400);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        TextField nameField = new TextField(existingName);
        nameField.setPromptText("Webhook name");
        nameField.setDisable(!existingName.isEmpty()); // Can't rename
        nameField.setMinWidth(350);

        TextField urlField = new TextField(existingUrl);
        urlField.setPromptText("https://discord.com/api/webhooks/...");
        urlField.setMinWidth(350);

        content.getChildren().addAll(new Label("Name:"), nameField, new Label("URL:"), urlField);

        dialog.getDialogPane().setContent(content);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(cancelType);
        if (saveBtn != null) saveBtn.getStyleClass().add("dialog-download-button");
        if (cancelBtn != null) cancelBtn.getStyleClass().add("dialog-cancel-button");

        dialog.setResultConverter(type -> {
            if (type == saveType) {
                return new String[]{nameField.getText().trim(), urlField.getText().trim()};
            }
            return null;
        });
        return dialog;
    }


    // Bot Tokens

    private VBox buildBotsSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");

        Label title = new Label("Bot Tokens");
        title.getStyleClass().add("card-title");

        Label desc = new Label("Configure tokens with channel IDs for bot-mode uploads and downloads");
        desc.getStyleClass().add("card-subtitle");

        botList = new VBox(8);
        refreshBotList();

        Button addButton = new Button("+ Add Bot Token");
        addButton.getStyleClass().addAll("btn-secondary", "btn-small");
        addButton.setOnAction(e -> showAddBotDialog());

        section.getChildren().addAll(title, desc, botList, addButton);
        return section;
    }

    private void refreshBotList() {
        botList.getChildren().clear();
        Configuration conf = Launcher.getConfiguration();
        LinkedHashMap<String, Configuration.BotProfile> bots = conf.getBots();

        if (bots.isEmpty()) {
            Label emptyLabel = new Label("No bot tokens configured");
            emptyLabel.getStyleClass().add("card-subtitle");
            emptyLabel.setPadding(new Insets(8));
            botList.getChildren().add(emptyLabel);
            return;
        }

        for (var entry : bots.entrySet()) {
            String name = entry.getKey();
            Configuration.BotProfile profile = entry.getValue();

            HBox item = new HBox(12);
            item.setAlignment(Pos.CENTER_LEFT);
            item.getStyleClass().add("profile-item");

            VBox info = new VBox(2);
            HBox.setHgrow(info, Priority.ALWAYS);

            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("profile-item-name");

            String tokenMasked = maskToken(profile.getToken());
            String channelDisplay = profile.getChannelId().isEmpty()
                    ? "(no channel)" : "Channel: " + profile.getChannelId();
            Label detailLabel = new Label(tokenMasked + "  •  " + channelDisplay);
            detailLabel.getStyleClass().add("profile-item-detail");

            info.getChildren().addAll(nameLabel, detailLabel);

            ImageView editImg = Resources.loadImageView("edit.png");
            editImg.setFitHeight(16);
            Button editBtn = new Button("", editImg);
            editBtn.getStyleClass().add("btn-icon");
            editBtn.setTooltip(new Tooltip("Edit"));
            editBtn.setOnAction(e -> showEditBotDialog(name, profile));

            ImageView deleteImg = Resources.loadImageView("close.png");
            deleteImg.setFitHeight(16);
            Button deleteBtn = new Button("", deleteImg);
            deleteBtn.getStyleClass().add("btn-icon");
            deleteBtn.setTooltip(new Tooltip("Delete"));
            deleteBtn.setOnAction(e -> {
                conf.removeBot(name);
                refreshBotList();
            });

            item.getChildren().addAll(info, editBtn, deleteBtn);
            botList.getChildren().add(item);
        }
    }

    private void showAddBotDialog() {
        Dialog<String[]> dialog = createBotDialog("Add Bot Token", "", "", "");
        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(values -> {
            if (!values[0].isEmpty() && !values[1].isEmpty()) {
                Launcher.getConfiguration().addBot(values[0], values[1], values[2]);
                refreshBotList();
            }
        });
    }

    private void showEditBotDialog(String name, Configuration.BotProfile profile) {
        Dialog<String[]> dialog = createBotDialog("Edit Bot Token", name, profile.getToken(), profile.getChannelId());
        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(values -> {
            Launcher.getConfiguration().updateBot(name, values[1], values[2]);
            refreshBotList();
        });
    }

    private Dialog<String[]> createBotDialog(String dialogTitle, String existingName,
                                             String existingToken, String existingChannel) {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle(dialogTitle);
        dialog.setResizable(true);

        try {
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/styles.css").toExternalForm());
        } catch (Exception ignored) {}
        dialog.getDialogPane().getStyleClass().add("file-info-dialog");
        dialog.getDialogPane().setPrefWidth(500);
        dialog.getDialogPane().setMinWidth(400);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        TextField nameField = new TextField(existingName);
        nameField.setPromptText("Bot profile name");
        nameField.setDisable(!existingName.isEmpty());
        nameField.setMinWidth(350);

        PasswordField tokenField = new PasswordField();
        tokenField.setText(existingToken);
        tokenField.setPromptText("Bot token");
        tokenField.setMinWidth(350);

        TextField channelField = new TextField(existingChannel);
        channelField.setPromptText("Channel ID");
        channelField.setMinWidth(350);

        Label channelHint = new Label("Channel ID is only required for uploading files");
        channelHint.getStyleClass().add("card-subtitle");
        channelHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #8e8ea0;");

        content.getChildren().addAll(
                new Label("Name:"), nameField,
                new Label("Bot Token:"), tokenField,
                new Label("Channel ID:"), channelField, channelHint
        );

        dialog.getDialogPane().setContent(content);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(cancelType);
        if (saveBtn != null) saveBtn.getStyleClass().add("dialog-download-button");
        if (cancelBtn != null) cancelBtn.getStyleClass().add("dialog-cancel-button");

        dialog.setResultConverter(type -> {
            if (type == saveType) {
                return new String[]{
                        nameField.getText().trim(),
                        tokenField.getText().trim(),
                        channelField.getText().trim()
                };
            }
            return null;
        });
        return dialog;
    }


    // Other settings

    private VBox buildGeneralSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");

        Label title = new Label("General");
        title.getStyleClass().add("card-title");

        Configuration conf = Launcher.getConfiguration();

        CheckBox prefetchBox = new CheckBox("Enable Prefetch (faster downloads)");
        prefetchBox.setSelected(conf.isPrefetchEnabled());
        prefetchBox.setOnAction(e -> conf.setPrefetchEnabled(prefetchBox.isSelected()));

        CheckBox clearTempBox = new CheckBox("Auto Remove Temp Files");
        clearTempBox.setSelected(conf.isClearTemp());
        clearTempBox.setOnAction(e -> conf.setClearTemp(clearTempBox.isSelected()));

        CheckBox hashBox = new CheckBox("Verify Part Hashes (SHA256)");
        hashBox.setSelected(conf.isCheckPartHash());
        hashBox.setOnAction(e -> conf.setCheckPartHash(hashBox.isSelected()));

        section.getChildren().addAll(title, prefetchBox, clearTempBox, hashBox);
        return section;
    }



    private String truncateUrl(String url) {
        if (url == null) return "(not set)";
        if (url.length() > 60) return url.substring(0, 57) + "...";
        return url;
    }

    private String maskToken(String token) {
        if (token == null || token.isEmpty()) return "(not set)";
        if (token.length() <= 8) return "****";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
