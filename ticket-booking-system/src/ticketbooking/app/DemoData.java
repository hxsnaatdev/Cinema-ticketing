package ticketbooking.app;

import ticketbooking.model.PremiumSeat;
import ticketbooking.model.RecliningSeat;
import ticketbooking.model.RegularSeat;
import ticketbooking.model.Show;
import ticketbooking.model.Theatre;

import java.time.LocalDateTime;

/**
 * Builds the sample data used by the GUI. The CLI keeps its own self-contained
 * setup in {@code Main} so the console demo can still be read top to bottom.
 */
public final class DemoData {

    private DemoData() {
    }

    /** A theatre with three shows: the catalogue, a waitlist show and a race show. */
    public static Theatre createTheatre() {
        Theatre theatre = new Theatre("PVR Manipal", "Jaipur");
        theatre.addShow(createMainShow());
        theatre.addShow(createWaitlistShow());
        theatre.addShow(createRaceShow());
        return theatre;
    }

    public static Show createMainShow() {
        Show show = new Show("SHOW-1", "Interstellar", "Screen 1",
                LocalDateTime.now().plusHours(3).withNano(0));
        show.addSeat(new RegularSeat("A", 1, 200));
        show.addSeat(new RegularSeat("A", 2, 200));
        show.addSeat(new PremiumSeat("B", 1, 200));
        show.addSeat(new PremiumSeat("B", 2, 200));
        show.addSeat(new RecliningSeat("C", 1, 200));
        show.addSeat(new RecliningSeat("C", 2, 200));
        return show;
    }

    public static Show createWaitlistShow() {
        Show show = new Show("SHOW-WAIT", "The Waitlist", "Screen 3",
                LocalDateTime.now().plusHours(2).withNano(0));
        show.addSeat(new PremiumSeat("W", 1, 200));
        return show;
    }

    public static Show createRaceShow() {
        Show show = new Show("SHOW-RACE", "Midnight Premiere", "Screen 2",
                LocalDateTime.now().plusHours(1).withNano(0));
        show.addSeat(new RegularSeat("L", 1, 200));
        return show;
    }
}
