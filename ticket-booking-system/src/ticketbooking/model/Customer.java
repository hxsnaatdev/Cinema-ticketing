package ticketbooking.model;

import ticketbooking.exception.SeatAlreadyBookedException;
import ticketbooking.service.BookingService;

/**
 * A customer is also the unit of work executed by a thread. Each customer
 * tries to book one seat in one show.
 */
public class Customer implements Runnable {

    private final String customerId;
    private final String name;
    private final String email;
    private final BookingService bookingService;
    private final String showId;
    private final String seatId;
    private final long waitTimeoutMs;

    public Customer(String customerId,
                    String name,
                    String email,
                    BookingService bookingService,
                    String showId,
                    String seatId,
                    long waitTimeoutMs) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.bookingService = bookingService;
        this.showId = showId;
        this.seatId = seatId;
        this.waitTimeoutMs = waitTimeoutMs;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        System.out.printf("[THREAD %s] %s is trying to book seat %s...%n",
                threadName, name, seatId);
        try {
            Booking booking = bookingService.book(showId, seatId, this, waitTimeoutMs);
            System.out.printf("[THREAD %s] SUCCESS: %s booked seat %s for Rs. %.2f (Booking %s)%n",
                    threadName, name, seatId, booking.getAmount(), booking.getBookingId());
        } catch (SeatAlreadyBookedException e) {
            System.out.printf("[THREAD %s] FAILED: %s could not book seat %s: %s%n",
                    threadName, name, seatId, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.printf("[THREAD %s] INTERRUPTED: %s stopped waiting for seat %s.%n",
                    threadName, name, seatId);
        }
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getShowId() {
        return showId;
    }

    public String getSeatId() {
        return seatId;
    }
}
