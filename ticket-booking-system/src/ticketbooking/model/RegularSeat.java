package ticketbooking.model;

/** Standard seat: charged at the base price. */
public class RegularSeat extends Seat {

    public RegularSeat(String row, int number, double basePrice) {
        super(row, number, basePrice);
    }

    @Override
    public String getSeatType() {
        return "Regular";
    }

    @Override
    public double calculatePrice() {
        return getBasePrice();
    }
}
