package ticketbooking.model;

/** Premium seat: 1.5x the base price. */
public class PremiumSeat extends Seat {

    private static final double PREMIUM_MULTIPLIER = 1.5;

    public PremiumSeat(String row, int number, double basePrice) {
        super(row, number, basePrice);
    }

    @Override
    public String getSeatType() {
        return "Premium";
    }

    @Override
    public double calculatePrice() {
        return getBasePrice() * PREMIUM_MULTIPLIER;
    }
}
