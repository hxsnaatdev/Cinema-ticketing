package ticketbooking.model;

/** Reclining seat: 2x the base price. */
public class RecliningSeat extends Seat {

    private static final double RECLINING_MULTIPLIER = 2.0;

    public RecliningSeat(String row, int number, double basePrice) {
        super(row, number, basePrice);
    }

    @Override
    public String getSeatType() {
        return "Reclining";
    }

    @Override
    public double calculatePrice() {
        return getBasePrice() * RECLINING_MULTIPLIER;
    }
}
