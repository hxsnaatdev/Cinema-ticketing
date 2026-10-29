package ticketbooking.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A theatre contains multiple shows. */
public class Theatre {

    private final String name;
    private final String location;
    private final Map<String, Show> shows = new LinkedHashMap<>();

    public Theatre(String name, String location) {
        this.name = name;
        this.location = location;
    }

    public synchronized void addShow(Show show) {
        if (shows.putIfAbsent(show.getShowId(), show) != null) {
            throw new IllegalArgumentException("Duplicate show id: " + show.getShowId());
        }
    }

    public synchronized Show getShow(String showId) {
        Show show = shows.get(showId);
        if (show == null) {
            throw new IllegalArgumentException("Unknown show id: " + showId);
        }
        return show;
    }

    public synchronized List<Show> getShowsSnapshot() {
        return new ArrayList<>(shows.values());
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }
}
