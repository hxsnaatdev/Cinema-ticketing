# Multithreaded Ticket Booking System

CSC2104 Object Oriented Programming mini project. A small cinema booking system
that demonstrates classes and objects, inheritance and polymorphism, custom
exceptions, file handling, multithreading, synchronization, and inter-thread
communication with `wait()` / `notifyAll()`.

It has two front ends over the same model and services:

- a scripted **CLI demo** (`ticketbooking.Main`)
- an interactive **Swing GUI** (`ticketbooking.ui.GuiMain`)

## Run

Requirements: JDK 17 or newer. The GUI uses only Swing, so there are no external
dependencies.

Linux, macOS, or Windows with Git Bash / WSL:

```bash
chmod +x run.sh
./run.sh          # CLI demo
./run.sh gui      # Swing GUI
```

Windows (Command Prompt or PowerShell):

```bat
run.bat           :: CLI demo
run.bat gui       :: Swing GUI
```

Or compile manually:

```bash
mkdir -p out
javac --release 17 -d out $(find src -name '*.java')
java -cp out ticketbooking.Main
java -cp out ticketbooking.ui.GuiMain
```

The program creates:

- `data/bookings.csv` - a full snapshot of every booking, including cancelled ones
- `receipts/BK-xxxx.txt` - a receipt for each confirmed booking
- `receipts/BK-xxxx-cancelled.txt` - a cancellation notice

## Using the GUI

1. Choose a show from the drop-down.
2. Click a seat. Green is available, pink is booked, blue is selected.
3. Enter a customer name and email.
4. Set **Wait (seconds)**:
   - `0` means fail immediately if the seat is already booked.
   - A positive value joins the waitlist and blocks a background thread until
     the seat is free or the timeout expires.
5. Press **Book Selected Seat**.
6. Select a row in the **Bookings** tab to cancel it or view its receipt.
7. Use **Race: 6 Customers** to release six booking tasks at once against the
   selected (or first available) seat.

The **Waitlist** tab shows the customers currently waiting. A 700 ms Swing timer
refreshes the waitlist and seat colours without rebuilding the seat map.

To demonstrate `wait()` / `notifyAll()`:

1. Book `W1` in the `The Waitlist` show as Alice with a wait of `0`.
2. Change the name to Bob, select the now-pink `W1`, set the wait to `30`, and
   press **Book Selected Seat**.
3. The status bar shows Bob waiting and the Waitlist tab shows his entry.
4. In the Bookings tab, select Alice's booking and press **Cancel Selected
   Booking**. Bob wakes up and books the freed seat.

All booking and cancellation work runs on a background `ExecutorService`; the
Swing event-dispatch thread only updates components. See `flow.md` for the full
thread flow.

## Project structure

```text
src/ticketbooking/
├── Main.java                         # CLI demo with concurrent customer threads
├── app/
│   └── DemoData.java                 # sample theatre/shows used by the GUI
├── model/
│   ├── Seat.java                     # abstract base class
│   ├── RegularSeat.java              # base price
│   ├── PremiumSeat.java              # 1.5x base price
│   ├── RecliningSeat.java            # 2x base price
│   ├── Customer.java                 # implements Runnable (one booking task)
│   ├── Booking.java                  # booking record and CSV serialization
│   ├── BookingStatus.java            # CONFIRMED / CANCELLED
│   ├── Show.java                     # synchronized booking + waitlist
│   └── Theatre.java                  # collection of shows
├── exception/
│   └── SeatAlreadyBookedException.java
├── service/
│   ├── BookingService.java           # facade for booking/cancel + side effects
│   ├── FileStorage.java              # atomic CSV persistence
│   └── ReceiptGenerator.java         # receipt file generation
└── ui/
    ├── GuiMain.java                  # Swing entry point
    ├── BookingFrame.java             # main window and background work
    ├── SeatMapPanel.java             # seat grid grouped by row
    ├── SeatButton.java               # coloured seat tile
    ├── BookingsTableModel.java       # bookings table model
    ├── WaitlistTableModel.java       # waitlist table model
    └── ReceiptViewer.java            # receipt text dialog
```

## What the demo shows

1. **Seat catalogue** - `Seat` references hold `RegularSeat`, `PremiumSeat`,
   and `RecliningSeat` objects. `calculatePrice()` is overridden by each
   subclass, so the correct price is chosen at runtime (polymorphism).
2. **Last-seat race** - six `Customer` threads start together and try to book
   the only seat `L1`. `Show.bookSeat()` is `synchronized`, so exactly one
   booking is confirmed. The other five join the waitlist and time out.
3. **Waitlist communication** - Alice books seat `W1`; Bob joins the waitlist
   and calls `wait()`. When Alice cancels, `Show.cancelBooking()` calls
   `notifyAll()`. Bob wakes up, re-checks the seat, and books it.
4. **Files** - every successful booking updates `data/bookings.csv` and writes
   a receipt. Cancellation writes a cancellation receipt.
5. **GUI** - the same operations are available interactively. The race button
   reproduces the six-customer contention, and the waitlist can be reproduced
   by booking a seat, waiting for it as another customer, then cancelling.

## Design notes

- The critical section is `Show.bookSeat()`. Because it is a `synchronized`
  instance method, only one customer can check and claim a seat at a time.
- `Seat` also synchronizes its `booked` flag as a second line of defence.
- The waitlist stores a `WaitlistEntry` per waiting customer. On cancellation,
  `notifyAll()` wakes all waiters; each one re-checks the seat in a `while`
  loop, so spurious wakeups and lost races are handled safely.
- Waiting has a timeout (`waitTimeoutMs`) so a demo thread cannot wait forever.
- CSV writes go to a temporary file first, then replace the real file. This
  avoids leaving a half-written snapshot on disk.
- Booking ids use an `AtomicLong`, which is safe across customer threads.
- The GUI never calls `book()` or `cancel()` on the event-dispatch thread. It
  submits the work to a daemon executor and applies the result back on the EDT.
- The GUI reads collections through snapshot getters (`getSeatsSnapshot()`,
  `getBookingsSnapshot()`, `getWaitlistSnapshot()`) and asks seats for their
  synchronized state, so it never exposes or mutates the model's internal
  collections.

## Documentation

- [`flow.md`](flow.md) - control flow, thread flow, and GUI flow
- [`decision.md`](decision.md) - design rationale and trade-offs
- [`demo.md`](demo.md) - presentation scripts for the CLI and GUI
