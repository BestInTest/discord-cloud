package yo.men.discordcloud.gui;

import yo.men.discordcloud.Main;
import yo.men.discordcloud.structure.WebHookManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ProgressGUI extends JFrame {

    private int progress; // liczba przesłanych kawałków
    private JTextField progressTextField; // ilość przesłanych kawałków pliku do ilości wszystkich kawałków (uploaded/all)
    private JProgressBar progressBar;

    public ProgressGUI(long progressMax, WebHookManager whm) {
        setTitle("Postęp zadania");
        setPreferredSize(new Dimension(400, 100));
        setLayout(new BorderLayout());
        setResizable(false);

        progressBar = new JProgressBar(0, (int) progressMax);
        progressBar.setStringPainted(true);
        progressBar.setValue(progress);

        progressTextField = new JTextField();
        progressTextField.setEditable(false);
        progressTextField.setText("Postęp: " + progress + "/" + progressBar.getMaximum());

        add(progressTextField, BorderLayout.NORTH);
        add(progressBar, BorderLayout.CENTER);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent ev) {
                //Zapytanie do użytkownika czy chce przerwać proces
                int dialogResult = JOptionPane.showConfirmDialog(ProgressGUI.this, "Czy na pewno chcesz anulować pobieranie/wysyłanie?\nMoże to spowodować błędy.", "Ostrzeżenie", JOptionPane.YES_NO_OPTION);
                if (dialogResult == JOptionPane.YES_OPTION) {
                    whm.forceClose(true);
                    dispose();
                }

            }
        });

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        Main.getStartGUI().setVisible(false); // ukrywanie menu głównego do czasu zakończenia operacji
    }

    // Jeżeli proces się zakończy, zwróci "true".
    // "false" oznacza, że zadanie nadal trwa.
    public boolean incrementProgress() {
        progress++;
        progressBar.setValue(progress);
        progressTextField.setText("Postęp: " + progress + "/" + progressBar.getMaximum());
        System.out.println(progress+"/"+progressBar.getMaximum());

        if (progress >= progressBar.getMaximum()) {
            JOptionPane.showMessageDialog(this, "Pobieranie/Wysyłanie zakończone.", "Sukces", JOptionPane.INFORMATION_MESSAGE);
            return true;
        }
        return false;
    }

    /*
    @Override
    public void dispose() {
        Main.getStartGUI().setVisible(true);
    }
    */
}
