package ticketbooking.ui;

import ticketbooking.model.Booking;
import ticketbooking.model.BookingStatus;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shows the receipt text that BookingService wrote for a booking. */
public final class ReceiptViewer {

    private ReceiptViewer() {
    }

    public static void show(Component parent, Booking booking) {
        Path directory = Path.of("receipts");
        Path file = findReceipt(directory, booking);
        if (file == null) {
            JOptionPane.showMessageDialog(parent,
                    "No receipt file found for " + booking.getBookingId()
                            + " in " + directory.toAbsolutePath(),
                    "Receipt not found",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JTextArea area = new JTextArea(text, 24, 64);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            area.setCaretPosition(0);

            JOptionPane.showMessageDialog(parent,
                    new JScrollPane(area),
                    "Receipt - " + booking.getBookingId() + " (" + file.getFileName() + ")",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "Could not read " + file + ": " + e.getMessage(),
                    "Receipt error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Path findReceipt(Path directory, Booking booking) {
        String id = booking.getBookingId();
        Path confirmed = directory.resolve(id + ".txt");
        Path cancelled = directory.resolve(id + "-cancelled.txt");

        if (booking.getStatus() == BookingStatus.CANCELLED && Files.exists(cancelled)) {
            return cancelled;
        }
        if (Files.exists(confirmed)) {
            return confirmed;
        }
        if (Files.exists(cancelled)) {
            return cancelled;
        }
        return null;
    }
}
