package ticketbooking.model;

import ticketbooking.exception.SeatAlreadyBookedException;

/**
 * Base class for every seat in a show.
 *
 * <p>Seat is abstract because every concrete seat type must provide its own
 * pricing rule. The booking flag is guarded by the Seat monitor as a second
 * line of defence; Show also guards the whole booking operation.</p>
 */
public abstract class Seat {

    private final String seatId;
    private final String row;
    private final int number;
    private final double basePrice;
    private boolean booked;
    private Customer bookedBy;

    protected Seat(String row, int number, double basePrice) {
        if (row == null || row.trim().isEmpty()) {
            throw new IllegalArgumentException("Seat row cannot be empty.");
        }
        if (number <= 0) {
            throw new IllegalArgumentException("Seat number must be positive.");
        }
        if (basePrice < 0) {
            throw new IllegalArgumentException("Base price cannot be negative.");
        }
        this.row = row.trim().toUpperCase();
        this.number = number;
        this.seatId = this.row + number;
        this.basePrice = basePrice;
    }

    /** Human-readable seat category used in receipts and reports. */
    public abstract String getSeatType();

    /** Price for this particular seat type. */
    public abstract double calculatePrice();

    public String getSeatId() {
        return seatId;
    }

    public String getRow() {
        return row;
    }

    public int getNumber() {
        return number;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public synchronized boolean isBooked() {
        return booked;
    }

    public synchronized Customer getBookedBy() {
        return bookedBy;
    }

    /**
     * Marks this seat as booked. Package-private so that only the model layer
     * (Show) can change seat state, keeping the synchronization policy in one
     * place.
     */
    synchronized void book(Customer customer) throws SeatAlreadyBookedException {
        if (booked) {
            String holder = bookedBy == null ? "another customer" : bookedBy.getName();
            throw new SeatAlreadyBookedException(
                    "Seat " + seatId + " is already booked by " + holder + ".");
        }
        this.booked = true;
        this.bookedBy = customer;
    }

    /** Releases the seat when a booking is cancelled. */
    synchronized void release() {
        this.booked = false;
        this.bookedBy = null;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - Rs. %.2f", seatId, getSeatType(), calculatePrice());
    }
}
