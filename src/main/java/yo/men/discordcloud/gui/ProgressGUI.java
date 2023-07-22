package yo.men.discordcloud.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ProgressGUI extends JFrame {

    private int progress; // liczba przesłanych kawałków
    private JTextField progressTextField; // ilość przesłanych kawałków pliku do ilości wszystkich kawałków (uploaded/all)
    private JProgressBar progressBar;

    public ProgressGUI(long progressMax) {
        setTitle("Wysyłanie/Pobieranie");
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
        System.out.println(progress+"/"+progressBar.getMaximum());

        if (progress >= progressBar.getMaximum()) {
            JOptionPane.showMessageDialog(this, "Wysyłanie/Pobieranie zakończone.", "Sukces", JOptionPane.INFORMATION_MESSAGE);
            setVisible(false);
            return true;
        }
        return false;
    }
}
