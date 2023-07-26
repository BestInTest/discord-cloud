package yo.men.discordcloud.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ProgressGUI extends JDialog {

    private int progress; // liczba przesłanych kawałków
    private JTextField progressTextField; // ilość przesłanych kawałków pliku do ilości wszystkich kawałków (uploaded/all)
    private JProgressBar progressBar;

    public ProgressGUI(long progressMax) {
        setTitle("Postęp zadania");
        setPreferredSize(new Dimension(400, 100));
        setLayout(new BorderLayout());

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
                int dialogResult = JOptionPane.showConfirmDialog(ProgressGUI.this, "Czy na pewno chcesz anulować pobieranie/wysyłanie?", "Ostrzeżenie", JOptionPane.YES_NO_OPTION);
                if (dialogResult == JOptionPane.YES_OPTION) {
                    //frame.dispose();
                    //todo przerywanie procesu. Pamiętać o poprawnym działaniu w przypadku gdy kliknięto 'Yes' kiedy proces się już zakończył
                }

            }
        });

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
            //setVisible(false);
            dispose();
            return true;
        }
        return false;
    }
}
