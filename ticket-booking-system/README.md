# Multithreaded Ticket Booking System

CSC2104 Object Oriented Programming mini project. A small cinema booking system
that demonstrates classes and objects, inheritance and polymorphism, custom
exceptions, file handling, multithreading, synchronization, and inter-thread
communication with `wait()` / `notifyAll()`.

## Run

Requirements: JDK 17 or newer.

```bash
chmod +x run.sh
./run.sh
```

Or compile manually:

```bash
mkdir -p out
javac --release 17 -d out $(find src -name '*.java')
java -cp out ticketbooking.Main
```

The program creates:

- `data/bookings.csv` - a full snapshot of every booking, including cancelled ones
- `receipts/BK-xxxx.txt` - a receipt for each confirmed booking
- `receipts/BK-xxxx-cancelled.txt` - a cancellation notice

## Project structure

```text
src/ticketbooking/
├── Main.java                         # demo with concurrent customer threads
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
└── service/
    ├── BookingService.java           # facade for booking/cancel + side effects
    ├── FileStorage.java              # atomic CSV persistence
    └── ReceiptGenerator.java         # receipt file generation
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

## Course concept mapping

| Handout topic | Where it appears |
| --- | --- |
| Classes and objects | `Theatre`, `Show`, `Seat`, `Customer`, `Booking` |
| Constructors and encapsulation | Model constructors, private fields, getters |
| Access control | Private state, package-private `Seat.book()` / `Seat.release()` |
| Inheritance | `Seat` -> `RegularSeat`, `PremiumSeat`, `RecliningSeat` |
| Method overriding / polymorphism | `getSeatType()` and `calculatePrice()` in seat subclasses |
| Abstract classes | `Seat` is abstract |
| Exception handling | `try/catch`, `throws`, custom checked `SeatAlreadyBookedException` |
| File handling | `FileStorage` (CSV), `ReceiptGenerator` (text receipts) |
| Creating threads | `Customer implements Runnable`, `new Thread(customer, name)` |
| Thread lifecycle | `start()`, `join()`, `interrupt()` |
| Synchronization | `synchronized` methods in `Show`, `Seat`, `FileStorage` |
| Inter-thread communication | `wait(remaining)` and `notifyAll()` in `Show` |
| Suspending/stopping threads | Wait timeouts, interruption handling, `finally` waitlist cleanup |

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
