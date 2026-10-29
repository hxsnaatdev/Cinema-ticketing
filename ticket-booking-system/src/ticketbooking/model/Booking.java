package ticketbooking.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable booking identity plus a mutable status. The atomic sequence keeps
 * booking ids unique even when many customer threads book at the same time.
 */
public class Booking {

    private static final AtomicLong SEQUENCE = new AtomicLong(1000);
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final String bookingId;
    private final Customer customer;
    private final Show show;
    private final Seat seat;
    private final double amount;
    private final LocalDateTime bookedAt;
    private volatile BookingStatus status;

    public Booking(Customer customer, Show show, Seat seat, double amount) {
        this.bookingId = "BK-" + SEQUENCE.incrementAndGet();
        this.customer = customer;
        this.show = show;
        this.seat = seat;
        this.amount = amount;
        this.bookedAt = LocalDateTime.now();
        this.status = BookingStatus.CONFIRMED;
    }

    void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    public String getBookingId() {
        return bookingId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Show getShow() {
        return show;
    }

    public Seat getSeat() {
        return seat;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getBookedAt() {
        return bookedAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    /** One CSV record, with quoting for values that contain commas or quotes. */
    public String toCsv() {
        return String.join(",",
                csv(bookingId),
                csv(customer.getCustomerId()),
                csv(customer.getName()),
                csv(show.getShowId()),
                csv(show.getTitle()),
                csv(seat.getSeatId()),
                csv(seat.getSeatType()),
                String.format("%.2f", amount),
                csv(bookedAt.format(CSV_TIME)),
                csv(status.name()));
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Override
    public String toString() {
        return String.format("%s | %s | seat %s | %s | Rs. %.2f",
                bookingId, customer.getName(), seat.getSeatId(), status, amount);
    }
}
