package ticketbooking.exception;

/**
 * Checked exception thrown when a customer tries to book a seat that is
 * already occupied (or when waiting for it times out).
 */
public class SeatAlreadyBookedException extends Exception {

    private static final long serialVersionUID = 1L;

    public SeatAlreadyBookedException(String message) {
        super(message);
    }

    public SeatAlreadyBookedException(String message, Throwable cause) {
        super(message, cause);
    }
}
