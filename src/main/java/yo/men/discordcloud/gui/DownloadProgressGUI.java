package yo.men.discordcloud.gui;

import javax.swing.*;
import java.awt.*;

/*
  UNUSED
 */
public class DownloadProgressGUI extends JFrame {

    private int progress; // liczba pobranych kawałków
    private JTextField progressTextField; // ilość pobranych kawałków pliku do ilości wszystkich kawałków (downloaded/all)
    private JProgressBar progressBar;

    public DownloadProgressGUI(long progressMax) {
        setTitle("Pobieranie");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(400, 100));
        setLayout(new BorderLayout());

        progressBar = new JProgressBar(0, (int) progressMax);
        progressBar.setStringPainted(true);

        progressTextField = new JTextField();
        progressTextField.setEditable(false);

        add(progressTextField, BorderLayout.NORTH);
        add(progressBar, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Jeżeli proces się zakończy, zwróci "true".
    // "false" oznacza, że zadanie nadal trwa.
    public boolean incrementProgress() {
        progress++;
        progressBar.setValue(progress);
        progressTextField.setText("Postęp: " + progress + "/" + progressBar.getMaximum());

        if (progress >= progressBar.getMaximum()) {
            JOptionPane.showMessageDialog(this, "Wysyłanie/Pobieranie zakończone.", "Sukces", JOptionPane.INFORMATION_MESSAGE);
            setVisible(false);
            return true;
        }
        return false;
    }
}
