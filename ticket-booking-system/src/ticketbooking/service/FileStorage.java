package ticketbooking.service;

import ticketbooking.model.Booking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

/**
 * Persists the current booking list to a CSV file. Each save writes a complete
 * snapshot to a temporary file and then replaces the real file, so readers
 * never see a half-written CSV.
 */
public class FileStorage {

    private static final String HEADER =
            "booking_id,customer_id,customer_name,show_id,show_title,seat_id,seat_type,amount,booked_at,status";

    private final Path dataDirectory;
    private final Path bookingsFile;

    public FileStorage(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory;
        Files.createDirectories(dataDirectory);
        this.bookingsFile = dataDirectory.resolve("bookings.csv");
    }

    public synchronized void saveBookings(Collection<Booking> bookings) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append(HEADER).append(System.lineSeparator());
        for (Booking booking : bookings) {
            csv.append(booking.toCsv()).append(System.lineSeparator());
        }

        Path temporary = Files.createTempFile(dataDirectory, "bookings-", ".tmp");
        Files.writeString(temporary, csv.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, bookingsFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, bookingsFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path getBookingsFile() {
        return bookingsFile;
    }
}
