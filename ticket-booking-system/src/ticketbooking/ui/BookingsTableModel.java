package ticketbooking.ui;

import ticketbooking.model.Booking;

import javax.swing.table.AbstractTableModel;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Read-only table model for a show's booking history. */
public class BookingsTableModel extends AbstractTableModel {

    public static final int STATUS_COLUMN = 5;

    private static final String[] COLUMNS = {
            "Booking ID", "Customer", "Seat", "Type", "Amount", "Status", "Booked at"
    };
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private List<Booking> bookings = new ArrayList<>();

    public void setBookings(List<Booking> bookings) {
        this.bookings = new ArrayList<>(bookings);
        fireTableDataChanged();
    }

    public Booking getBookingAt(int row) {
        if (row < 0 || row >= bookings.size()) {
            return null;
        }
        return bookings.get(row);
    }

    @Override
    public int getRowCount() {
        return bookings.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Booking booking = bookings.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return booking.getBookingId();
            case 1:
                return booking.getCustomer().getName();
            case 2:
                return booking.getSeat().getSeatId();
            case 3:
                return booking.getSeat().getSeatType();
            case 4:
                return String.format("Rs. %.2f", booking.getAmount());
            case 5:
                return booking.getStatus().name();
            case 6:
                return booking.getBookedAt().format(DISPLAY_TIME);
            default:
                return "";
        }
    }
}
