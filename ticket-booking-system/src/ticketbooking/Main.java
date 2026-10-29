package ticketbooking;

import ticketbooking.model.Booking;
import ticketbooking.model.Customer;
import ticketbooking.model.PremiumSeat;
import ticketbooking.model.RecliningSeat;
import ticketbooking.model.RegularSeat;
import ticketbooking.model.Seat;
import ticketbooking.model.Show;
import ticketbooking.model.Theatre;
import ticketbooking.service.BookingService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

public class Main {

    private static final long WAIT_TIMEOUT_MS = 2500;

    public static void main(String[] args) throws Exception {
        printBanner();

        Theatre theatre = new Theatre("PVR Manipal", "Jaipur");
        Show interstellar = createMainShow();
        theatre.addShow(interstellar);

        BookingService service = new BookingService(theatre);

        System.out.printf("Theatre: %s, %s%n", theatre.getName(), theatre.getLocation());
        printSeatCatalogue(interstellar);

        demonstrateLastSeatRace(theatre, service);
        Booking bobBooking = demonstrateWaitlist(theatre, service);

        printGeneratedFiles(bobBooking);
    }

    private static void printBanner() {
        System.out.println("==============================================================");
        System.out.println(" MULTITHREADED TICKET BOOKING SYSTEM - CSC2104 MINI PROJECT");
        System.out.println("==============================================================");
    }

    private static Show createMainShow() {
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

    private static void printSeatCatalogue(Show show) {
        System.out.println("\nSeat catalogue for \"" + show.getTitle()
                + "\" (" + show.getShowId() + "):");
        for (Seat seat : show.getSeatsSnapshot()) {
            System.out.printf("  %-3s %-10s Rs. %7.2f%n",
                    seat.getSeatId(), seat.getSeatType(), seat.calculatePrice());
        }
        System.out.printf("Available seats: %d%n", show.getAvailableSeatCount());
    }

    /**
     * Six customers start at the same instant and all try to book the only
     * seat. Show.bookSeat is synchronized, so exactly one can win.
     */
    private static void demonstrateLastSeatRace(Theatre theatre, BookingService service)
            throws InterruptedException {
        System.out.println("\n--- DEMO 1: Last-seat race (synchronized prevents double booking) ---");

        Show show = new Show("SHOW-RACE", "Midnight Premiere", "Screen 2",
                LocalDateTime.now().plusHours(1).withNano(0));
        show.addSeat(new RegularSeat("L", 1, 200));
        theatre.addShow(show);

        int customerCount = 6;
        CountDownLatch startGate = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int i = 1; i <= customerCount; i++) {
            Customer customer = new Customer(
                    "C" + i,
                    "Customer-" + i,
                    "customer" + i + "@example.com",
                    service,
                    show.getShowId(),
                    "L1",
                    WAIT_TIMEOUT_MS);

            Thread thread = new Thread(() -> {
                try {
                    startGate.await();
                    customer.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Customer-" + i);

            threads.add(thread);
            thread.start();
        }

        startGate.countDown();
        Thread.sleep(400);
        System.out.printf("[MAIN] Customers currently on the waitlist: %d%n",
                show.getWaitlistSnapshot().size());

        for (Thread thread : threads) {
            thread.join();
        }

        long confirmed = show.getConfirmedBookings().size();
        System.out.printf("[RESULT] Confirmed bookings for seat L1: %d (exactly 1 expected)%n",
                confirmed);
        System.out.printf("[RESULT] Waitlist after all threads finish: %d%n",
                show.getWaitlistSnapshot().size());
    }

    /**
     * Alice books the only seat. Bob joins the waitlist and calls wait().
     * When Alice cancels, notifyAll() wakes Bob and he books the freed seat.
     */
    private static Booking demonstrateWaitlist(Theatre theatre, BookingService service)
            throws Exception {
        System.out.println("\n--- DEMO 2: Waitlist with wait() / notify() ---");

        Show show = new Show("SHOW-WAIT", "The Waitlist", "Screen 3",
                LocalDateTime.now().plusHours(2).withNano(0));
        show.addSeat(new PremiumSeat("W", 1, 200));
        theatre.addShow(show);

        Customer alice = new Customer("C-ALICE", "Alice", "alice@example.com",
                service, show.getShowId(), "W1", 0);
        Booking aliceBooking = service.book(show.getShowId(), "W1", alice);
        System.out.printf("[MAIN] Alice booked seat W1 (%s). Bob will now wait for it.%n",
                aliceBooking.getBookingId());

        Customer bob = new Customer("C-BOB", "Bob", "bob@example.com",
                service, show.getShowId(), "W1", 5000);
        Thread bobThread = new Thread(bob, "Bob-Waitlist");
        bobThread.start();

        Thread.sleep(1000);
        System.out.printf("[MAIN] Waitlist size before cancellation: %d%n",
                show.getWaitlistSnapshot().size());
        System.out.println("[MAIN] Alice cancels her booking now.");
        service.cancel(aliceBooking);

        bobThread.join(6000);
        if (bobThread.isAlive()) {
            bobThread.interrupt();
            bobThread.join();
        }

        System.out.println("[RESULT] Bookings for show " + show.getShowId() + ":");
        for (Booking booking : show.getBookingsSnapshot()) {
            System.out.println("  " + booking);
        }

        List<Booking> confirmed = show.getConfirmedBookings();
        if (confirmed.size() == 1 && confirmed.get(0).getCustomer().getName().equals("Bob")) {
            System.out.println("[RESULT] Bob was woken by notifyAll() and booked seat W1.");
            return confirmed.get(0);
        }
        throw new IllegalStateException("Expected Bob to get the seat after Alice cancelled.");
    }

    private static void printGeneratedFiles(Booking sampleBooking) throws Exception {
        System.out.println("\n--- Generated files ---");
        System.out.println("Bookings CSV : " + Path.of("data", "bookings.csv").toAbsolutePath());
        System.out.println("Receipts dir : " + Path.of("receipts").toAbsolutePath());

        System.out.println("\nReceipt files:");
        try (Stream<Path> files = Files.list(Path.of("receipts"))) {
            files.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> System.out.println("  " + path.getFileName()));
        }

        System.out.println("\nSample receipt for " + sampleBooking.getBookingId() + ":");
        System.out.println(Files.readString(
                Path.of("receipts", sampleBooking.getBookingId() + ".txt")));
    }
}
