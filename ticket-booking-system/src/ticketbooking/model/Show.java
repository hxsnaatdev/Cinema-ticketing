package ticketbooking.model;

import ticketbooking.exception.SeatAlreadyBookedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single screening. This class owns the critical section for booking and the
 * waitlist used for inter-thread communication.
 *
 * <p>Only one thread can execute a synchronized method on a Show object at a
 * time. That is what prevents two customer threads from booking the same seat.
 * When a seat is released, notifyAll() wakes the waitlisted threads so they can
 * re-check the seat and compete for it.</p>
 */
public class Show {

    private final String showId;
    private final String title;
    private final String hall;
    private final LocalDateTime showTime;

    private final Map<String, Seat> seats = new LinkedHashMap<>();
    private final List<Booking> bookings = new ArrayList<>();
    private final List<WaitlistEntry> waitlist = new ArrayList<>();

    public Show(String showId, String title, String hall, LocalDateTime showTime) {
        this.showId = showId;
        this.title = title;
        this.hall = hall;
        this.showTime = showTime;
    }

    public synchronized void addSeat(Seat seat) {
        if (seats.putIfAbsent(seat.getSeatId(), seat) != null) {
            throw new IllegalArgumentException("Duplicate seat id: " + seat.getSeatId());
        }
    }

    /**
     * Books a seat or waits on the waitlist until it becomes available.
     *
     * @param waitTimeoutMs maximum time to wait for an occupied seat
     * @throws SeatAlreadyBookedException if the seat is taken and the wait times out
     * @throws InterruptedException       if the waiting thread is interrupted
     */
    public synchronized Booking bookSeat(Customer customer, String seatId, long waitTimeoutMs)
            throws SeatAlreadyBookedException, InterruptedException {

        Seat seat = seats.get(seatId);
        if (seat == null) {
            throw new IllegalArgumentException(
                    "Seat " + seatId + " does not exist in show " + showId + ".");
        }

        if (seat.isBooked()) {
            WaitlistEntry entry = new WaitlistEntry(customer, seatId);
            waitlist.add(entry);
            System.out.printf("[WAITLIST] %s joined the waitlist for seat %s in \"%s\". Waiting: %d%n",
                    customer.getName(), seatId, title, waitlist.size());

            long deadline = System.currentTimeMillis() + Math.max(0, waitTimeoutMs);
            try {
                while (seat.isBooked()) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        throw new SeatAlreadyBookedException(
                                "Seat " + seatId + " is occupied and " + customer.getName()
                                        + " timed out after " + waitTimeoutMs + " ms.");
                    }
                    wait(remaining);
                }
            } finally {
                waitlist.remove(entry);
            }
        }

        // Still inside the Show monitor, so no other thread can take this seat
        // between the check above and the call below.
        seat.book(customer);
        Booking booking = new Booking(customer, this, seat, seat.calculatePrice());
        bookings.add(booking);
        return booking;
    }

    /** Cancels a booking, frees the seat and wakes every waiting customer. */
    public synchronized void cancelBooking(Booking booking) {
        if (booking == null || !bookings.contains(booking)) {
            throw new IllegalArgumentException("Booking does not belong to show " + showId + ".");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }

        booking.cancel();
        booking.getSeat().release();
        System.out.printf("[SHOW %s] Booking %s cancelled; seat %s is free. Calling notifyAll()...%n",
                showId, booking.getBookingId(), booking.getSeat().getSeatId());
        notifyAll();
    }

    public synchronized int getAvailableSeatCount() {
        int count = 0;
        for (Seat seat : seats.values()) {
            if (!seat.isBooked()) {
                count++;
            }
        }
        return count;
    }

    public synchronized List<Seat> getSeatsSnapshot() {
        return new ArrayList<>(seats.values());
    }

    public synchronized List<Booking> getBookingsSnapshot() {
        return new ArrayList<>(bookings);
    }

    public synchronized List<Booking> getConfirmedBookings() {
        List<Booking> confirmed = new ArrayList<>();
        for (Booking booking : bookings) {
            if (booking.getStatus() == BookingStatus.CONFIRMED) {
                confirmed.add(booking);
            }
        }
        return confirmed;
    }

    public synchronized List<WaitlistEntry> getWaitlistSnapshot() {
        return new ArrayList<>(waitlist);
    }

    public String getShowId() {
        return showId;
    }

    public String getTitle() {
        return title;
    }

    public String getHall() {
        return hall;
    }

    public LocalDateTime getShowTime() {
        return showTime;
    }

    /** One customer waiting for one seat. */
    public static final class WaitlistEntry {
        private final Customer customer;
        private final String seatId;
        private final LocalDateTime requestedAt;

        private WaitlistEntry(Customer customer, String seatId) {
            this.customer = customer;
            this.seatId = seatId;
            this.requestedAt = LocalDateTime.now();
        }

        public Customer getCustomer() {
            return customer;
        }

        public String getSeatId() {
            return seatId;
        }

        public LocalDateTime getRequestedAt() {
            return requestedAt;
        }

        @Override
        public String toString() {
            return customer.getName() + " -> " + seatId + " since " + requestedAt;
        }
    }
}
