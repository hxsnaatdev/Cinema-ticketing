package ticketbooking.ui;

import ticketbooking.exception.SeatAlreadyBookedException;
import ticketbooking.model.Booking;
import ticketbooking.model.BookingStatus;
import ticketbooking.model.Customer;
import ticketbooking.model.Seat;
import ticketbooking.model.Show;
import ticketbooking.model.Theatre;
import ticketbooking.service.BookingService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Swing client for the booking service. All booking and cancellation work runs
 * on a background executor; the EDT only updates components.
 */
public class BookingFrame extends JFrame {

    private static final int RACE_CUSTOMER_COUNT = 6;
    private static final long RACE_WAIT_TIMEOUT_MS = 2500L;
    private static final Color CANCELLED_COLOR = new Color(160, 40, 40);
    private static final Color CONFIRMED_COLOR = new Color(20, 110, 50);

    private final Theatre theatre;
    private final BookingService service;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "gui-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong customerSequence = new AtomicLong(1);

    private final JComboBox<Show> showCombo;
    private final SeatMapPanel seatMap;
    private final BookingsTableModel bookingsModel = new BookingsTableModel();
    private final WaitlistTableModel waitlistModel = new WaitlistTableModel();
    private final JTable bookingsTable;
    private final JTable waitlistTable;
    private final JTextField nameField = new JTextField("Alice", 14);
    private final JTextField emailField = new JTextField("alice@example.com", 14);
    private final JSpinner waitSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 120, 5));
    private JSplitPane splitPane;
    private final JButton bookButton = new JButton("Book Selected Seat");
    private final JButton cancelButton = new JButton("Cancel Selected Booking");
    private final JButton receiptButton = new JButton("View Receipt");
    private final JButton raceButton = new JButton("Race: 6 Customers");
    private final JLabel selectionLabel = new JLabel(" ");
    private final JLabel availableLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel("Ready.");
    private final Timer refreshTimer;

    private boolean manualBookingInFlight;
    private boolean cancelInFlight;
    private boolean raceInFlight;

    public BookingFrame(Theatre theatre) throws IOException {
        super("Multithreaded Ticket Booking System");
        this.theatre = theatre;
        this.service = new BookingService(theatre);
        this.seatMap = new SeatMapPanel(this::onSeatSelected);
        this.showCombo = new JComboBox<>(theatre.getShowsSnapshot().toArray(new Show[0]));
        this.bookingsTable = new JTable(bookingsModel);
        this.waitlistTable = new JTable(waitlistModel);

        configureShowCombo();
        configureTables();
        configureActions();

        setLayout(new BorderLayout(8, 8));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildMainArea(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        refreshTimer = new Timer(700, event -> refreshLiveState());
        refreshTimer.start();

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                refreshTimer.stop();
                executor.shutdownNow();
                dispose();
                System.exit(0);
            }
        });

        setMinimumSize(new Dimension(900, 600));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1120, screen.width - 80), Math.min(760, screen.height - 100));
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.62));
        setLocationRelativeTo(null);

        onShowChanged();
    }

    private void configureShowCombo() {
        showCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Show) {
                    Show show = (Show) value;
                    setText(show.getTitle() + " (" + show.getShowId() + ")");
                }
                return this;
            }
        });
        showCombo.addActionListener(event -> onShowChanged());
    }

    private void configureTables() {
        bookingsTable.setFillsViewportHeight(true);
        bookingsTable.setRowHeight(24);
        bookingsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookingsTable.setAutoCreateRowSorter(true);
        bookingsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateActionButtons();
            }
        });
        bookingsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                int modelRow = table.convertRowIndexToModel(row);
                Booking booking = bookingsModel.getBookingAt(modelRow);

                if (isSelected) {
                    component.setForeground(table.getSelectionForeground());
                    component.setFont(table.getFont());
                } else if (booking != null && column == BookingsTableModel.STATUS_COLUMN) {
                    component.setForeground(booking.getStatus() == BookingStatus.CANCELLED
                            ? CANCELLED_COLOR : CONFIRMED_COLOR);
                    component.setFont(table.getFont().deriveFont(Font.BOLD));
                } else {
                    component.setForeground(table.getForeground());
                    component.setFont(table.getFont());
                }
                return component;
            }
        });

        waitlistTable.setFillsViewportHeight(true);
        waitlistTable.setRowHeight(24);
        waitlistTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        bookingsTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        bookingsTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        bookingsTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        bookingsTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        bookingsTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        bookingsTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        bookingsTable.getColumnModel().getColumn(6).setPreferredWidth(150);
    }

    private void configureActions() {
        bookButton.addActionListener(event -> bookSelectedSeat());
        cancelButton.addActionListener(event -> cancelSelectedBooking());
        receiptButton.addActionListener(event -> showSelectedReceipt());
        raceButton.addActionListener(event -> runRaceDemo());

        bookButton.setToolTipText("Book the selected seat for the customer in the form");
        cancelButton.setToolTipText("Cancel the booking selected in the table");
        receiptButton.setToolTipText("Show the generated receipt file");
        raceButton.setToolTipText("Start six customers at once against one seat");
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        header.setBorder(new EmptyBorder(2, 8, 0, 8));

        JLabel theatreLabel = new JLabel("<html><b>" + theatre.getName() + "</b> | "
                + theatre.getLocation() + "</html>");
        header.add(theatreLabel);
        header.add(Box.createHorizontalStrut(12));
        header.add(new JLabel("Show:"));
        header.add(showCombo);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(event -> refreshAll());
        header.add(refreshButton);

        header.add(Box.createHorizontalStrut(12));
        availableLabel.setFont(availableLabel.getFont().deriveFont(Font.BOLD));
        header.add(availableLabel);
        return header;
    }

    private JComponent buildMainArea() {
        JPanel seatHolder = new JPanel(new BorderLayout());
        seatHolder.setBorder(BorderFactory.createTitledBorder("Seat map"));
        JScrollPane seatScroll = new JScrollPane(seatMap);
        seatScroll.setBorder(BorderFactory.createEmptyBorder());
        seatHolder.add(seatScroll, BorderLayout.CENTER);

        JPanel upper = new JPanel(new BorderLayout(8, 8));
        upper.add(seatHolder, BorderLayout.CENTER);
        upper.add(buildSidePanel(), BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Bookings", new JScrollPane(bookingsTable));
        tabs.addTab("Waitlist", new JScrollPane(waitlistTable));

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upper, tabs);
        splitPane.setResizeWeight(0.62);
        splitPane.setDividerSize(8);
        splitPane.setOneTouchExpandable(true);
        splitPane.setBorder(new EmptyBorder(0, 8, 0, 8));
        return splitPane;
    }

    private JComponent buildSidePanel() {
        JPanel side = new JPanel(new GridBagLayout());
        side.setBorder(BorderFactory.createTitledBorder("Booking"));
        side.setPreferredSize(new Dimension(340, 0));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;

        addFormRow(form, constraints, 0, "Customer name:", nameField);
        addFormRow(form, constraints, 1, "Email:", emailField);
        addFormRow(form, constraints, 2, "Wait (seconds):", waitSpinner);

        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.anchor = GridBagConstraints.NORTHWEST;

        row.gridy = 0;
        row.insets = new Insets(4, 6, 0, 6);
        side.add(form, row);

        selectionLabel.setBorder(new EmptyBorder(0, 6, 0, 6));
        row.gridy = 1;
        row.insets = new Insets(10, 6, 0, 6);
        side.add(selectionLabel, row);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 6));
        actions.add(bookButton);
        actions.add(cancelButton);
        actions.add(receiptButton);
        actions.add(raceButton);
        row.gridy = 2;
        row.insets = new Insets(0, 6, 0, 6);
        side.add(actions, row);

        JTextArea hint = new JTextArea("Select any seat to book it. To join the waitlist, "
                + "select a booked seat and set a wait time above zero, then cancel the "
                + "holder's booking to wake the waiter.", 5, 20);
        hint.setEditable(false);
        hint.setFocusable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setOpaque(false);
        hint.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 12f));
        hint.setForeground(new Color(80, 80, 80));
        hint.setBorder(new EmptyBorder(0, 0, 0, 0));
        row.gridy = 3;
        row.insets = new Insets(12, 6, 6, 6);
        side.add(hint, row);

        row.gridy = 4;
        row.weighty = 1;
        row.fill = GridBagConstraints.BOTH;
        side.add(Box.createVerticalGlue(), row);
        return side;
    }

    private void addFormRow(JPanel form, GridBagConstraints constraints, int row,
            String label, JComponent component) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 0;
        form.add(new JLabel(label), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(component, constraints);
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)),
                new EmptyBorder(6, 12, 6, 12)));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        bar.add(statusLabel, BorderLayout.CENTER);
        return bar;
    }

    private void onShowChanged() {
        Show show = selectedShow();
        seatMap.setShow(show);
        if (show == null) {
            return;
        }
        refreshAll();
        setStatus("Loaded \"" + show.getTitle() + "\" (" + show.getShowId() + ").");
    }

    private void onSeatSelected(Seat seat) {
        updateSelectionLabel();
        updateActionButtons();
        setStatus("Selected seat " + seat.getSeatId() + " (" + seat.getSeatType() + ").");
    }

    private void refreshLiveState() {
        Show show = selectedShow();
        if (show == null) {
            return;
        }
        waitlistModel.setEntries(show.getWaitlistSnapshot());
        int total = show.getSeatsSnapshot().size();
        int available = show.getAvailableSeatCount();
        availableLabel.setText("Available: " + available + " / " + total);
        seatMap.refresh();
        updateSelectionLabel();
        updateActionButtons();
    }

    private void refreshAll() {
        Show show = selectedShow();
        if (show == null) {
            return;
        }
        String preferredBookingId = selectedBookingId();
        bookingsModel.setBookings(show.getBookingsSnapshot());
        waitlistModel.setEntries(show.getWaitlistSnapshot());
        seatMap.refresh();

        int total = show.getSeatsSnapshot().size();
        int available = show.getAvailableSeatCount();
        availableLabel.setText("Available: " + available + " / " + total);

        selectBookingById(preferredBookingId);
        updateSelectionLabel();
        updateActionButtons();
    }

    private void updateSelectionLabel() {
        Seat seat = seatMap.getSelectedSeat();
        if (seat == null) {
            selectionLabel.setText("<html><i>No seat selected.</i></html>");
            return;
        }

        String state;
        if (seat.isBooked()) {
            Customer holder = seat.getBookedBy();
            state = "booked" + (holder == null ? "" : " by " + holder.getName());
        } else {
            state = "available";
        }

        selectionLabel.setText(String.format(
                "<html><b>Selected: %s</b> (%s)<br>Rs. %.2f - %s</html>",
                seat.getSeatId(), seat.getSeatType(), seat.calculatePrice(), state));
    }

    private void updateActionButtons() {
        Seat seat = seatMap.getSelectedSeat();
        bookButton.setEnabled(seat != null && !manualBookingInFlight && !raceInFlight);
        raceButton.setEnabled(selectedShow() != null && !raceInFlight && !manualBookingInFlight);

        Booking booking = selectedBooking();
        cancelButton.setEnabled(booking != null
                && booking.getStatus() == BookingStatus.CONFIRMED
                && !cancelInFlight);
        receiptButton.setEnabled(booking != null);
    }

    private void bookSelectedSeat() {
        Show show = selectedShow();
        Seat seat = seatMap.getSelectedSeat();
        if (show == null || seat == null) {
            setStatus("Select a seat first.");
            return;
        }

        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        if (name.isEmpty()) {
            showValidation("Enter a customer name.");
            nameField.requestFocusInWindow();
            return;
        }
        if (email.indexOf('@') <= 0 || email.endsWith("@")) {
            showValidation("Enter a valid email address.");
            emailField.requestFocusInWindow();
            return;
        }

        int waitSeconds = ((Number) waitSpinner.getValue()).intValue();
        long waitTimeoutMs = waitSeconds * 1000L;
        String customerId = "C-GUI-" + customerSequence.getAndIncrement();
        Customer customer = new Customer(customerId, name, email, service,
                show.getShowId(), seat.getSeatId(), waitTimeoutMs);

        manualBookingInFlight = true;
        updateActionButtons();
        setStatus(waitTimeoutMs > 0
                ? "Booking " + seat.getSeatId() + " for " + name + "; waiting up to "
                        + waitSeconds + "s..."
                : "Booking " + seat.getSeatId() + " for " + name + "...");

        runAsync(() -> service.book(show.getShowId(), seat.getSeatId(), customer, waitTimeoutMs),
                booking -> {
                    manualBookingInFlight = false;
                    updateActionButtons();
                    refreshAll();
                    selectBookingById(booking.getBookingId());
                    setStatus("Confirmed " + booking.getBookingId() + ": " + name
                            + " booked seat " + seat.getSeatId() + " for Rs. "
                            + String.format("%.2f", booking.getAmount()) + ".");
                },
                error -> {
                    manualBookingInFlight = false;
                    updateActionButtons();
                    refreshAll();
                    if (error instanceof SeatAlreadyBookedException) {
                        setStatus("Booking failed: " + error.getMessage());
                        JOptionPane.showMessageDialog(this, error.getMessage(),
                                "Seat unavailable", JOptionPane.WARNING_MESSAGE);
                    } else {
                        showError("Booking failed", error);
                    }
                });
    }

    private void cancelSelectedBooking() {
        Booking booking = selectedBooking();
        if (booking == null) {
            setStatus("Select a booking in the table first.");
            return;
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            setStatus("That booking is already cancelled.");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Cancel " + booking.getBookingId() + " for "
                        + booking.getCustomer().getName() + "?",
                "Cancel booking", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        cancelInFlight = true;
        updateActionButtons();
        setStatus("Cancelling " + booking.getBookingId() + "...");

        runAsync(() -> {
            service.cancel(booking);
            return booking;
        }, cancelled -> {
            cancelInFlight = false;
            updateActionButtons();
            refreshAll();
            selectBookingById(cancelled.getBookingId());
            setStatus("Cancelled " + cancelled.getBookingId() + "; seat "
                    + cancelled.getSeat().getSeatId() + " is free and waiters were notified.");
        }, error -> {
            cancelInFlight = false;
            updateActionButtons();
            refreshAll();
            showError("Cancel failed", error);
        });
    }

    private void showSelectedReceipt() {
        Booking booking = selectedBooking();
        if (booking == null) {
            setStatus("Select a booking in the table first.");
            return;
        }
        ReceiptViewer.show(this, booking);
    }

    private void runRaceDemo() {
        Show show = selectedShow();
        if (show == null) {
            return;
        }
        Seat target = chooseRaceSeat(show);
        if (target == null) {
            setStatus("No available seat in \"" + show.getTitle() + "\" for the race.");
            return;
        }

        raceInFlight = true;
        updateActionButtons();
        setStatus("Releasing " + RACE_CUSTOMER_COUNT + " customers at once for seat "
                + target.getSeatId() + "...");

        runAsync(() -> runRace(show, target),
                result -> {
                    raceInFlight = false;
                    updateActionButtons();
                    refreshAll();
                    setStatus("Race complete for seat " + target.getSeatId() + ".");
                    showTextDialog("Race result", result);
                },
                error -> {
                    raceInFlight = false;
                    updateActionButtons();
                    refreshAll();
                    showError("Race failed", error);
                });
    }

    private Seat chooseRaceSeat(Show show) {
        Seat selected = seatMap.getSelectedSeat();
        if (selected != null && !selected.isBooked()) {
            return selected;
        }
        for (Seat seat : show.getSeatsSnapshot()) {
            if (!seat.isBooked()) {
                return seat;
            }
        }
        return null;
    }

    private String runRace(Show show, Seat target) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(RACE_CUSTOMER_COUNT, runnable -> {
            Thread thread = new Thread(runnable, "race-worker");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int i = 1; i <= RACE_CUSTOMER_COUNT; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    Customer customer = new Customer("C-RACE-" + index, "Race-" + index,
                            "race" + index + "@example.com", service, show.getShowId(),
                            target.getSeatId(), RACE_WAIT_TIMEOUT_MS);
                    try {
                        startGate.await();
                        Booking booking = service.book(show.getShowId(), target.getSeatId(),
                                customer, RACE_WAIT_TIMEOUT_MS);
                        return "Race-" + index + " SUCCESS: " + booking.getBookingId()
                                + " (Rs. " + String.format("%.2f", booking.getAmount()) + ")";
                    } catch (SeatAlreadyBookedException e) {
                        return "Race-" + index + " FAILED: timed out";
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return "Race-" + index + " INTERRUPTED";
                    }
                }));
            }
            startGate.countDown();

            StringBuilder summary = new StringBuilder();
            summary.append("Seat ").append(target.getSeatId())
                    .append(" in \"").append(show.getTitle()).append("\"\n\n");

            int successes = 0;
            int failures = 0;
            for (Future<String> future : futures) {
                String line = future.get();
                summary.append(line).append('\n');
                if (line.contains("SUCCESS")) {
                    successes++;
                } else {
                    failures++;
                }
            }

            long confirmed = show.getConfirmedBookings().stream()
                    .filter(booking -> booking.getSeat().getSeatId()
                            .equals(target.getSeatId()))
                    .count();

            summary.append("\nConfirmed bookings for this seat: ").append(confirmed).append('\n');
            summary.append("Successes: ").append(successes)
                    .append(", failures: ").append(failures);
            return summary.toString();
        } finally {
            pool.shutdownNow();
        }
    }

    private void showTextDialog(String title, String text) {
        JTextArea area = new JTextArea(text, 14, 60);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), title,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private Booking selectedBooking() {
        int viewRow = bookingsTable.getSelectedRow();
        if (viewRow < 0 || viewRow >= bookingsTable.getRowCount()) {
            return null;
        }
        int modelRow = bookingsTable.convertRowIndexToModel(viewRow);
        return bookingsModel.getBookingAt(modelRow);
    }

    private String selectedBookingId() {
        Booking booking = selectedBooking();
        return booking == null ? null : booking.getBookingId();
    }

    private void selectBookingById(String bookingId) {
        if (bookingId == null) {
            return;
        }
        for (int viewRow = 0; viewRow < bookingsTable.getRowCount(); viewRow++) {
            int modelRow = bookingsTable.convertRowIndexToModel(viewRow);
            Booking booking = bookingsModel.getBookingAt(modelRow);
            if (booking != null && bookingId.equals(booking.getBookingId())) {
                bookingsTable.setRowSelectionInterval(viewRow, viewRow);
                bookingsTable.scrollRectToVisible(
                        bookingsTable.getCellRect(viewRow, 0, true));
                return;
            }
        }
    }

    private Show selectedShow() {
        return (Show) showCombo.getSelectedItem();
    }

    private void showValidation(String message) {
        setStatus(message);
        JOptionPane.showMessageDialog(this, message, "Invalid input",
                JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String title, Throwable error) {
        String message = error.getMessage() == null ? error.toString() : error.getMessage();
        setStatus(title + ": " + message);
        JOptionPane.showMessageDialog(this, message, title,
                JOptionPane.ERROR_MESSAGE);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
        statusLabel.setToolTipText(message);
    }

    private <T> void runAsync(Callable<T> task, Consumer<T> onSuccess,
            Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                T result = task.call();
                SwingUtilities.invokeLater(() -> onSuccess.accept(result));
            } catch (Throwable error) {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                SwingUtilities.invokeLater(() -> onError.accept(error));
            }
        });
    }
}
