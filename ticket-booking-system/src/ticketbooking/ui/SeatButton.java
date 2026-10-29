package ticketbooking.ui;

import ticketbooking.model.Seat;

import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** A coloured seat tile. Booked seats remain clickable so a customer can wait. */
public class SeatButton extends JButton {

    private static final Color AVAILABLE = new Color(198, 232, 198);
    private static final Color BOOKED = new Color(242, 176, 176);
    private static final Color SELECTED = new Color(142, 186, 255);
    private static final Color BORDER = new Color(90, 90, 90);
    private static final Color SELECTED_BORDER = new Color(20, 80, 200);

    private final Seat seat;
    private boolean seatSelected;

    public SeatButton(Seat seat) {
        this.seat = seat;
        setPreferredSize(new java.awt.Dimension(84, 56));
        setFont(getFont().deriveFont(Font.BOLD, 12f));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setBorderPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refresh();
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeatSelected(boolean selected) {
        this.seatSelected = selected;
    }

    /** Pulls the latest booked state from the model; called by the refresh timer. */
    public void refresh() {
        setText("<html><center>" + seat.getSeatId()
                + "<br><span style='font-size:8px'>" + seat.getSeatType()
                + "</span></center></html>");
        setToolTipText(seat.getSeatId() + " - " + seat.getSeatType()
                + " - Rs. " + String.format("%.2f", seat.calculatePrice())
                + (seat.isBooked() ? " (booked)" : " (available)"));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        Color fill = seat.isBooked() ? BOOKED : (seatSelected ? SELECTED : AVAILABLE);
        if (getModel().isPressed()) {
            fill = fill.darker();
        } else if (getModel().isRollover()) {
            fill = fill.brighter();
        }

        g2.setColor(fill);
        g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12));
        g2.dispose();

        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(seatSelected ? SELECTED_BORDER : BORDER);
        g2.setStroke(new BasicStroke(seatSelected ? 2f : 1f));
        g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12));
        g2.dispose();
    }
}
