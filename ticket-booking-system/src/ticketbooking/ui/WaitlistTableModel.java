package ticketbooking.ui;

import ticketbooking.model.Show;

import javax.swing.table.AbstractTableModel;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Read-only table model for the customers currently waiting on a show. */
public class WaitlistTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Customer", "Seat", "Requested at"};
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private List<Show.WaitlistEntry> entries = new ArrayList<>();

    public void setEntries(List<Show.WaitlistEntry> entries) {
        this.entries = new ArrayList<>(entries);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return entries.size();
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
        Show.WaitlistEntry entry = entries.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return entry.getCustomer().getName();
            case 1:
                return entry.getSeatId();
            case 2:
                return entry.getRequestedAt().format(DISPLAY_TIME);
            default:
                return "";
        }
    }
}
