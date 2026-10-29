package ticketbooking.ui;

import ticketbooking.app.DemoData;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;

/** Entry point for the Swing interface. */
public final class GuiMain {

    private GuiMain() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The default look and feel is fine if the system one is unavailable.
        }

        SwingUtilities.invokeLater(() -> {
            try {
                BookingFrame frame = new BookingFrame(DemoData.createTheatre());
                frame.setVisible(true);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Could not initialise storage: " + e.getMessage(),
                        "Startup error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
