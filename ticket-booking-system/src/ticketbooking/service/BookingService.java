package ticketbooking.service;

import ticketbooking.exception.SeatAlreadyBookedException;
import ticketbooking.model.Booking;
import ticketbooking.model.Customer;
import ticketbooking.model.Show;
import ticketbooking.model.Theatre;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Application-level facade: delegates seat locking to Show and handles the
 * side effects (CSV persistence and receipt files) after a booking succeeds.
 */
public class BookingService {

    private final Theatre theatre;
    private final FileStorage storage;
    private final ReceiptGenerator receiptGenerator;
    private final List<Booking> allBookings = new ArrayList<>();

    public BookingService(Theatre theatre) throws IOException {
        this.theatre = theatre;
        this.storage = new FileStorage(Path.of("data"));
        this.receiptGenerator = new ReceiptGenerator(Path.of("receipts"));
    }

    public Booking book(String showId, String seatId, Customer customer)
            throws SeatAlreadyBookedException, InterruptedException {
        return book(showId, seatId, customer, 0);
    }

    public Booking book(String showId, String seatId, Customer customer, long waitTimeoutMs)
            throws SeatAlreadyBookedException, InterruptedException {
        Show show = theatre.getShow(showId);
        Booking booking = show.bookSeat(customer, seatId, waitTimeoutMs);
        recordBooking(booking);
        return booking;
    }

    public void cancel(Booking booking) {
        Show show = booking.getShow();
        show.cancelBooking(booking);
        recordCancellation(booking);
    }

    private synchronized void recordBooking(Booking booking) {
        allBookings.add(booking);
        persistAllBookings();
        try {
            Path receipt = receiptGenerator.generate(booking);
            System.out.printf("[RECEIPT] %s -> %s%n",
                    booking.getBookingId(), receipt.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[WARNING] Booking confirmed but receipt could not be written: "
                    + e.getMessage());
        }
    }

    private synchronized void recordCancellation(Booking booking) {
        persistAllBookings();
        try {
            Path receipt = receiptGenerator.generateCancellation(booking);
            System.out.printf("[RECEIPT] Cancellation notice -> %s%n", receipt.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[WARNING] Cancellation receipt could not be written: "
                    + e.getMessage());
        }
    }

    private void persistAllBookings() {
        try {
            storage.saveBookings(new ArrayList<>(allBookings));
            System.out.printf("[FILE] Bookings persisted -> %s%n",
                    storage.getBookingsFile().toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[WARNING] Could not persist bookings: " + e.getMessage());
        }
    }

    public synchronized List<Booking> getAllBookings() {
        return new ArrayList<>(allBookings);
    }

    public Theatre getTheatre() {
        return theatre;
    }
}
