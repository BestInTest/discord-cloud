package yo.men.discordcloud.gui;

import yo.men.discordcloud.Main;
import yo.men.discordcloud.structure.DiscordFileStruct;
import yo.men.discordcloud.structure.WebHookManager;
import yo.men.discordcloud.utils.FileHelper;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class FileExplorerGUI extends JFrame {

    private final String STORAGE_DIR_PATH = new File(Main.STORAGE_DIR).getAbsolutePath(); // STORAGE_DIR_PATH ??

    private JList<File> fileList;
    private DefaultListModel<File> listModel;
    private JTextArea currentPathLabel;
    private File currentDirectory;

    //Przyciski
    JButton uploadButton = new JButton("Upload");
    JButton downloadButton = new JButton("Download");
    JButton refreshButton = new JButton("Refresh");
    JButton settingsButton = new JButton("Settings");

    public FileExplorerGUI() {
        setTitle("File Explorer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
        fileList.setCellRenderer(new FileListCellRenderer());
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = fileList.locationToIndex(e.getPoint());
                    File selectedFile = listModel.getElementAt(index);
                    if (selectedFile.isDirectory()) {
                        displayFilesAndDirectories(selectedFile.getAbsolutePath());
                    } else if (selectedFile.isFile()) {
                        //TODO: FileInfoGUI (do tego zrobić jakiś parser plików json czy coś)
                    }
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(fileList);

        currentPathLabel = new JTextArea();
        currentPathLabel.setEditable(false);
        currentPathLabel.setBackground(UIManager.getColor("Label.background"));
        currentPathLabel.setBorder(UIManager.getBorder("Label.border"));
        JScrollPane pathScrollPane = new JScrollPane(currentPathLabel);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(uploadButton);
        buttonPanel.add(downloadButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(settingsButton);

        add(buttonPanel, BorderLayout.NORTH);
        add(pathScrollPane, BorderLayout.SOUTH);
        add(scrollPane, BorderLayout.CENTER);

        uploadButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser(".");
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                Settings settings = Main.getSettings();
                WebHookManager uploader = new WebHookManager(settings.getWebhookUrl(), Main.MAX_FILE_SIZE);
                Thread uploadThread = new Thread(() -> {
                    try {
                        uploader.sendFile(selectedFile);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null, "Wystąpił błąd: " + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                        throw new RuntimeException(ex);
                    } finally {
                        enableButtons();
                        refreshFileList();
                    }
                });
                disableButtons();
                uploadThread.start();
            }
        });

        downloadButton.addActionListener(e -> {
            // Implementacja logiki przycisku Download
            Settings settings = Main.getSettings();
            WebHookManager downloader = new WebHookManager(settings.getWebhookUrl(), Main.MAX_FILE_SIZE);
            Thread downloadThread = new Thread(() -> {
                try {
                    int index = fileList.getSelectedIndex();
                    File selectedFile = listModel.getElementAt(index);
                    DiscordFileStruct f = FileHelper.loadStructureFile(selectedFile);
                    downloader.downloadFile(f);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Wystąpił błąd: " + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    throw new RuntimeException(ex);
                } finally {
                    enableButtons();
                    refreshFileList();
                }
            });
            disableButtons();
            downloadThread.start();
        });

        refreshButton.addActionListener(e -> {
            // Implementacja logiki przycisku odświeżania
            refreshFileList();
        });

        settingsButton.addActionListener(e -> {
            // Implementacja logiki przycisku ustawień
            //FIXME: po otwarciu ustawień cały czas można klikać przyciski okna głównego
            //Main.showSettingsGUI(false);
        });

    }

    public void displayFilesAndDirectories(String path) {
        currentDirectory = new File(path);
        File[] files = currentDirectory.listFiles();

        listModel.clear();

        if (files != null) {
            String currDirPath = currentDirectory.getAbsolutePath();
            if (!currDirPath.equals(STORAGE_DIR_PATH)) {
                if (currentDirectory.getParentFile() != null) {
                    listModel.addElement(currentDirectory.getParentFile()); // Dodawanie przycisku do powrotu do folderu nadzrzędnego
                }
            }

            for (File file : files) {
                if (file.isDirectory() || file.getName().endsWith(".json")) {
                    listModel.addElement(file); // Dodawanie tylko folderów i plików z rozszerzeniem .json
                }
            }
        }

        currentPathLabel.setText(currentDirectory.getAbsolutePath());

        FileListCellRenderer renderer = (FileListCellRenderer) fileList.getCellRenderer();
        renderer.setParentDirectory(currentDirectory.getParentFile(), currentDirectory.getAbsolutePath());

        fileList.clearSelection();
        fileList.repaint();
    }

    private class FileListCellRenderer extends DefaultListCellRenderer {
        private FileSystemView fileSystemView;
        private File parentDirectory;
        private String currentDirectoryPath;

        public FileListCellRenderer() {
            fileSystemView = FileSystemView.getFileSystemView();
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index,
                    isSelected, cellHasFocus);

            File file = (File) value;

            if (parentDirectory != null && file.equals(parentDirectory)) {
                Icon icon = UIManager.getIcon("FileView.directoryIcon");
                label.setIcon(icon);

                String parentDirectoryName = (file.getAbsolutePath().equals(currentDirectoryPath)) ? "." : "..";
                label.setText(parentDirectoryName);
            } else {
                label.setIcon(fileSystemView.getSystemIcon(file));
                label.setText(fileSystemView.getSystemDisplayName(file));
            }

            return label;
        }

        public void setParentDirectory(File parentDirectory, String currentDirectoryPath) {
            this.parentDirectory = parentDirectory;
            this.currentDirectoryPath = currentDirectoryPath;
        }
    }

    private void disableButtons() {
        uploadButton.setEnabled(false);
        downloadButton.setEnabled(false);
        refreshButton.setEnabled(false);
        settingsButton.setEnabled(false);
    }

    private void enableButtons() {
        uploadButton.setEnabled(true);
        downloadButton.setEnabled(true);
        refreshButton.setEnabled(true);
        settingsButton.setEnabled(true);
    }

    public void refreshFileList() {
        displayFilesAndDirectories(currentDirectory.toString());
    }
}
