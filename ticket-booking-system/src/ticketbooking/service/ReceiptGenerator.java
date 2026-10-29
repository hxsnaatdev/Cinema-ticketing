package ticketbooking.service;

import ticketbooking.model.Booking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Creates a plain-text receipt for every confirmed or cancelled booking. */
public class ReceiptGenerator {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final String RULE =
            "==============================================================";

    private final Path receiptDirectory;

    public ReceiptGenerator(Path receiptDirectory) throws IOException {
        this.receiptDirectory = receiptDirectory;
        Files.createDirectories(receiptDirectory);
    }

    public synchronized Path generate(Booking booking) throws IOException {
        Path file = receiptDirectory.resolve(booking.getBookingId() + ".txt");
        Files.writeString(file, buildReceipt(booking, false), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    public synchronized Path generateCancellation(Booking booking) throws IOException {
        Path file = receiptDirectory.resolve(booking.getBookingId() + "-cancelled.txt");
        Files.writeString(file, buildReceipt(booking, true), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    private String buildReceipt(Booking booking, boolean cancellation) {
        StringBuilder text = new StringBuilder();
        text.append(RULE).append(System.lineSeparator());
        text.append("                 CINEPLEX TICKET RECEIPT").append(System.lineSeparator());
        text.append(RULE).append(System.lineSeparator());
        text.append(String.format("Booking ID   : %s%n", booking.getBookingId()));
        text.append(String.format("Status       : %s%n", booking.getStatus()));
        text.append(String.format("Customer     : %s (%s)%n",
                booking.getCustomer().getName(), booking.getCustomer().getCustomerId()));
        text.append(String.format("Email        : %s%n", booking.getCustomer().getEmail()));
        text.append(String.format("Movie        : %s%n", booking.getShow().getTitle()));
        text.append(String.format("Hall         : %s%n", booking.getShow().getHall()));
        text.append(String.format("Show time    : %s%n",
                booking.getShow().getShowTime().format(DISPLAY_TIME)));
        text.append(String.format("Seat         : %s (%s)%n",
                booking.getSeat().getSeatId(), booking.getSeat().getSeatType()));
        text.append(String.format("Amount       : Rs. %.2f%n", booking.getAmount()));
        text.append(String.format("Booked at    : %s%n", booking.getBookedAt().format(DISPLAY_TIME)));
        if (cancellation) {
            text.append(String.format("Cancelled at : %s%n",
                    LocalDateTime.now().format(DISPLAY_TIME)));
        }
        text.append(RULE).append(System.lineSeparator());
        text.append("      Thank you for booking with us. Enjoy the show!").append(System.lineSeparator());
        text.append(RULE).append(System.lineSeparator());
        return text.toString();
    }
}
