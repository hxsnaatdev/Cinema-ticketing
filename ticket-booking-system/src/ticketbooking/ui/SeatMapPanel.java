package ticketbooking.ui;

import ticketbooking.model.Seat;
import ticketbooking.model.Show;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Renders a show's seats as one horizontal row per seat row. */
public class SeatMapPanel extends JPanel {

    private final Consumer<Seat> onSeatSelected;
    private final JPanel rowsPanel = new JPanel();
    private final List<SeatButton> seatButtons = new ArrayList<>();
    private Seat selectedSeat;

    public SeatMapPanel(Consumer<Seat> onSeatSelected) {
        super(new BorderLayout(0, 8));
        this.onSeatSelected = onSeatSelected;
        setBorder(new EmptyBorder(8, 8, 8, 8));
        add(buildLegend(), BorderLayout.NORTH);

        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        add(rowsPanel, BorderLayout.CENTER);
    }

    public void setShow(Show show) {
        selectedSeat = null;
        seatButtons.clear();
        rowsPanel.removeAll();

        if (show != null) {
            List<Seat> seats = show.getSeatsSnapshot();
            seats.sort(Comparator.comparing(Seat::getRow)
                    .thenComparingInt(Seat::getNumber));

            Map<String, List<Seat>> rows = new LinkedHashMap<>();
            for (Seat seat : seats) {
                rows.computeIfAbsent(seat.getRow(), key -> new ArrayList<>()).add(seat);
            }

            for (Map.Entry<String, List<Seat>> entry : rows.entrySet()) {
                JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
                rowPanel.setOpaque(false);
                rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel rowLabel = new JLabel(entry.getKey());
                rowLabel.setPreferredSize(new Dimension(22, 40));
                rowLabel.setFont(rowLabel.getFont().deriveFont(Font.BOLD, 14f));
                rowPanel.add(rowLabel);

                for (Seat seat : entry.getValue()) {
                    SeatButton button = new SeatButton(seat);
                    button.addActionListener(e -> {
                        selectedSeat = seat;
                        onSeatSelected.accept(seat);
                        refresh();
                    });
                    seatButtons.add(button);
                    rowPanel.add(button);
                }
                rowsPanel.add(rowPanel);
            }
        }

        revalidate();
        repaint();
    }

    public Seat getSelectedSeat() {
        return selectedSeat;
    }

    public void clearSelection() {
        selectedSeat = null;
        refresh();
    }

    public void refresh() {
        for (SeatButton button : seatButtons) {
            button.setSeatSelected(button.getSeat() == selectedSeat);
            button.refresh();
        }
        rowsPanel.repaint();
    }

    private JPanel buildLegend() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        legend.setOpaque(false);
        legend.add(swatch(new Color(198, 232, 198), "Available"));
        legend.add(swatch(new Color(242, 176, 176), "Booked"));
        legend.add(swatch(new Color(142, 186, 255), "Selected"));

        JLabel prices = new JLabel("Regular 1x | Premium 1.5x | Reclining 2x");
        prices.setFont(prices.getFont().deriveFont(11f));
        prices.setForeground(new Color(90, 90, 90));
        legend.add(prices);
        return legend;
    }

    private JPanel swatch(Color color, String text) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);

        JLabel box = new JLabel();
        box.setOpaque(true);
        box.setBackground(color);
        box.setPreferredSize(new Dimension(16, 16));
        box.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 120)));

        panel.add(box);
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(11f));
        panel.add(label);
        return panel;
    }
}
