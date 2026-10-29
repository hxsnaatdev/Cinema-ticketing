# Code Flow

This document traces what happens when the program runs, from `main()` to the
files written on disk. It focuses on **control flow** and **thread flow**;
design rationale is in [`decision.md`](decision.md) and the presentation script
is in [`demo.md`](demo.md).

## 1. Big picture

The code is split into three layers:

| Layer | Package | Responsibility |
| --- | --- | --- |
| Presentation / demo | `ticketbooking` | `Main` builds the objects and runs two demonstrations |
| Domain model | `ticketbooking.model` | `Theatre`, `Show`, `Seat`, `Customer`, `Booking` and the booking rules |
| Services | `ticketbooking.service` | `BookingService` facade, CSV persistence, receipt writing |
| Exception | `ticketbooking.exception` | `SeatAlreadyBookedException` |

```text
Main
 ├── creates Theatre, Show, Seats
 ├── creates BookingService (creates data/ and receipts/)
 ├── Demo 1: 6 Customer threads race for one seat
 ├── Demo 2: Bob waits for Alice's seat, then cancellation wakes him
 └── prints generated files

Customer (Runnable)
 └── BookingService.book(...)
      └── Show.bookSeat(...)          <-- critical section
           ├── Seat.book(...)         <-- marks the seat taken
           └── new Booking(...)
      └── recordBooking(...)          <-- CSV + receipt, after the lock is released
```

## 2. Startup and object wiring

`Main.main()` (`src/ticketbooking/Main.java:26`) performs the setup in this
order:

1. `printBanner()` (`Main.java:44`) prints a header.
2. A `Theatre` is created: `"PVR Manipal"` in `"Jaipur"` (`Main.java:29`).
3. `createMainShow()` (`Main.java:50`) creates show `SHOW-1` / `"Interstellar"`
   and adds six seats:
   - `A1`, `A2` → `RegularSeat` (base price)
   - `B1`, `B2` → `PremiumSeat` (1.5× base price)
   - `C1`, `C2` → `RecliningSeat` (2× base price)
4. The show is registered with the theatre via `Theatre.addShow()`
   (`Theatre.java:20`).
5. `new BookingService(theatre)` (`Main.java:33`) constructs:
   - `FileStorage(Path.of("data"))` (`BookingService.java:27`) which creates the
     `data/` directory and points at `data/bookings.csv`
     (`FileStorage.java:26-29`).
   - `ReceiptGenerator(Path.of("receipts"))` (`BookingService.java:28`) which
     creates `receipts/` (`ReceiptGenerator.java:23-25`).
6. `printSeatCatalogue()` (`Main.java:62`) takes a snapshot of the seats and
   prints each seat's type and price. The price comes from
   `seat.calculatePrice()`, so the subclass override decides the value
   (`Seat.java:41`).

### Why the prices differ

`Seat` is abstract and declares `calculatePrice()`. Each subclass overrides it:

| Class | `calculatePrice()` | Base 200 → |
| --- | --- | --- |
| `RegularSeat` | `getBasePrice()` (`RegularSeat.java:16`) | 200.00 |
| `PremiumSeat` | `getBasePrice() * 1.5` (`PremiumSeat.java:18`) | 300.00 |
| `RecliningSeat` | `getBasePrice() * 2.0` (`RecliningSeat.java:18`) | 400.00 |

`Main` only holds `Seat` references. The JVM dispatches to the real subclass at
runtime, which is the polymorphism part of the project.

## 3. The normal booking path

Every successful booking follows the same chain:

```text
Customer.run()                                  Customer.java:37
 └── BookingService.book(showId, seatId, c, t)  BookingService.java:36
      ├── Theatre.getShow(showId)               Theatre.java:26
      └── Show.bookSeat(customer, seatId, t)    Show.java:51   [locks Show]
           ├── seats.get(seatId)                Show.java:54
           ├── seat.isBooked()                  Show.java:60   [locks Seat]
           ├── seat.book(customer)              Show.java:84   [locks Seat]
           ├── new Booking(...)                 Booking.java:24
           └── bookings.add(booking)             Show.java:86
      └── recordBooking(booking)                BookingService.java:50 [locks service]
           ├── persistAllBookings()             BookingService.java:74
           │    └── FileStorage.saveBookings()  FileStorage.java:32
           └── receiptGenerator.generate()      ReceiptGenerator.java:28
```

Important detail: `Show.bookSeat()` is `synchronized`, so the check "is the seat
free?", the state change "mark it booked", and the creation of the `Booking`
are one atomic operation with respect to other threads using the same `Show`
object. Persistence and receipt writing happen **after** `bookSeat()` returns,
still inside `BookingService` but outside the `Show` lock.

## 4. Demo 1 — the last-seat race

Code: `Main.demonstrateLastSeatRace()` (`Main.java:76-126`).

### Setup

- A new show `SHOW-RACE` / `"Midnight Premiere"` is created with exactly one
  seat, `L1` (`Main.java:80-83`).
- Six `Customer` objects are created, all targeting `L1` with a 2,500 ms
  timeout (`Main.java:89-97`).
- A `CountDownLatch` with count 1 acts as a starting gate (`Main.java:86`).
  Every thread calls `startGate.await()` and blocks. `startGate.countDown()`
  (`Main.java:112`) releases all six at nearly the same instant, which makes
  the race reproducible.

### Timeline

```text
time 0 ms   all 6 threads wake from the latch and call customer.run()
            -> BookingService.book(...) -> Show.bookSeat(...)

            exactly one thread (the winner) enters the synchronized method first
            -> seat is free
            -> seat.book(winner)
            -> new Booking(...)                   (e.g. BK-1001)
            -> bookings.add(...)
            -> returns to BookingService
            -> CSV + receipt are written
            -> Customer.run prints "SUCCESS"

time ~0 ms  the other 5 threads enter bookSeat one at a time
            -> seat.isBooked() == true
            -> waitlist.add(new WaitlistEntry(...))
            -> wait(remaining)                    (releases the Show lock)

time 400 ms Main prints the waitlist size (typically 5)

time 2500ms each waiter's wait() times out
            -> loop re-checks seat.isBooked()
            -> remaining <= 0
            -> throw SeatAlreadyBookedException
            -> finally: waitlist.remove(entry)
            -> Customer.run catches the exception and prints "FAILED"
time ...    Main joins every thread
            -> confirmed bookings for L1 = 1
            -> waitlist size = 0
```

### Key lines

- Waitlist insertion: `Show.java:60-64`.
- Deadline and timeout loop: `Show.java:66-76`.
- The `finally` cleanup that removes the entry on success, timeout, or
  interrupt: `Show.java:77-79`.
- Atomic claim: `Show.java:84-86`.
- Exception handling in the worker: `Customer.java:45-47`.

Because `wait()` releases the `Show` monitor, other customers can still enter
`bookSeat()` and join the waitlist while the seat is held. If the seat were
released, every waiter would re-check the same `seat.isBooked()` condition.

## 5. Demo 2 — waitlist and `wait()` / `notifyAll()`

Code: `Main.demonstrateWaitlist()` (`Main.java:132-175`).

### Setup

- A new show `SHOW-WAIT` / `"The Waitlist"` has one premium seat, `W1`
  (`Main.java:136-139`).
- Alice books `W1` synchronously with a timeout of `0`
  (`Main.java:141-143`). This is a normal booking, so it produces BK-1002 in a
  clean run.
- Bob is a `Customer` targeting the same seat with a 5,000 ms timeout
  (`Main.java:147-150`). He runs on a separate thread named `Bob-Waitlist`.

### Timeline

```text
t = 0     Alice's booking completes: seat W1 is booked by Alice.

t ≈ 0     Bob enters Show.bookSeat()
          -> seat.isBooked() == true
          -> waitlist.add(Bob's entry)           (waitlist size = 1)
          -> wait(5000)                          (Show lock released)

t = 1s    Main prints "Waitlist size before cancellation: 1".
          Main calls BookingService.cancel(aliceBooking).

          BookingService.cancel()                BookingService.java:44
           └── Show.cancelBooking()              Show.java:91   [locks Show]
                ├── verify the booking belongs to this show
                ├── booking.cancel()             -> status = CANCELLED
                ├── seat.release()               -> booked = false
                └── notifyAll()                  -> wakes Bob

t ≈ 1s    BookingService.recordCancellation()
          -> rewrite data/bookings.csv
          -> write receipts/BK-1002-cancelled.txt

t ≈ 1s    Bob re-acquires the Show lock after cancelBooking() returns
          -> while (seat.isBooked()) is now false
          -> finally: waitlist.remove(Bob's entry)
          -> seat.book(Bob)
          -> new Booking(...)                    (e.g. BK-1003)
          -> bookings.add(...)
          -> recordBooking(): CSV + receipt
          -> Bob prints "SUCCESS"
```

### Key lines

- `cancelBooking()` validation, status change, seat release, and wake-up:
  `Show.java:91-104`.
- `notifyAll()`: `Show.java:103` — all waiters are woken, because they may be
  waiting for different seats. Each waiter re-checks its own condition.
- The `while` loop around `wait()`: `Show.java:68-76` — required for spurious
  wake-ups and for the case where another thread takes the seat first.
- Bob's success check: `Main.java:169-174`.

### Log order is not guaranteed

The `[FILE]`, `[RECEIPT]`, and `[THREAD]` lines can interleave slightly
differently on different runs. The final state is deterministic:
Alice is `CANCELLED`, Bob is `CONFIRMED`, and the seat ends up booked by Bob.

## 6. Cancellation path

`BookingService.cancel()` (`BookingService.java:44-48`):

```text
booking.getShow()
 └── Show.cancelBooking(booking)          [Show lock]
      ├── reject null / foreign bookings
      ├── ignore an already-cancelled booking
      ├── booking.cancel()       -> BookingStatus.CANCELLED
      ├── seat.release()         -> Seat.booked = false, bookedBy = null
      └── notifyAll()
 └── recordCancellation(booking)          [service lock]
      ├── persistAllBookings()            -> full CSV snapshot
      └── receiptGenerator.generateCancellation() -> *-cancelled.txt
```

The booking object is **not** removed. It stays in `Show.bookings` with status
`CANCELLED`, so history is preserved in the CSV and in the receipt files.

## 7. Persistence flow

Code: `FileStorage.saveBookings()` (`FileStorage.java:32-48`).

1. Build the whole CSV in memory, starting with the fixed header
   (`FileStorage.java:20-21`).
2. Append one line per `Booking` using `Booking.toCsv()`
   (`Booking.java:67-79`). Values containing commas, quotes, or newlines are
   quoted and escaped (`Booking.java:81-89`).
3. Write the text to a temporary file in `data/`
   (`FileStorage.java:39-40`).
4. Replace `data/bookings.csv` with the temporary file using
   `ATOMIC_MOVE` (`FileStorage.java:42-44`).
5. If the file system does not support atomic moves, fall back to a normal
   replace (`FileStorage.java:45-47`).

Consequence: a reader never sees a half-written CSV. The final CSV is a
**complete snapshot**, not an append-only log, so a cancellation simply
changes the row's `status` to `CANCELLED` on the next save.

`BookingService` serialises all of this with `synchronized`
(`BookingService.java:50` and `:63`), so concurrent successful bookings cannot
write the CSV at the same time.

## 8. Receipt flow

Code: `ReceiptGenerator` (`ReceiptGenerator.java`).

- A successful booking writes `receipts/<bookingId>.txt` (`:28-33`).
- A cancellation writes `receipts/<bookingId>-cancelled.txt` (`:35-40`).
- Both use `buildReceipt()` (`:42-68`) which formats booking, customer, show,
  seat, amount, and timestamps.
- Files are written as UTF-8 and overwrite an existing file of the same name.

I/O failures are caught inside `BookingService.recordBooking()` and
`recordCancellation()` (`BookingService.java:53-60`, `:65-71`). A failed
receipt or CSV write only prints a warning; the in-memory booking remains
confirmed.

## 9. Locking model

| Lock | Guards | Methods |
| --- | --- | --- |
| `Show` monitor | seat map, bookings list, waitlist, booking/cancel decisions | `addSeat`, `bookSeat`, `cancelBooking`, `get*Snapshot`, `getAvailableSeatCount` (`Show.java:38-136`) |
| `Seat` monitor | `booked` flag and `bookedBy` | `isBooked`, `getBookedBy`, `book`, `release` (`Seat.java:59-86`) |
| `BookingService` monitor | `allBookings` and side-effect ordering | `recordBooking`, `recordCancellation`, `getAllBookings` (`BookingService.java:50-86`) |
| `FileStorage` monitor | CSV writes | `saveBookings` (`FileStorage.java:32`) |
| `Theatre` monitor | shows map | `addShow`, `getShow`, `getShowsSnapshot` (`Theatre.java:20-36`) |
| `Booking.status` | visible status flag | `volatile` field (`Booking.java:22`) |
| `Booking.SEQUENCE` | unique booking ids | `AtomicLong.incrementAndGet()` (`Booking.java:13`, `:25`) |

Lock ordering is always `Show → Seat` (`Show.bookSeat` and
`Show.cancelBooking`). `Seat` never calls back into `Show`, so there is no
reverse order and therefore no lock-ordering deadlock.

`wait()` on the `Show` monitor releases that monitor, which is why other
threads can enter `bookSeat()` and wait as well.

## 10. Exception paths

| Situation | Exception | Thrown at | Caught where |
| --- | --- | --- | --- |
| Unknown show id | `IllegalArgumentException` | `Theatre.getShow()` (`Theatre.java:26-32`) | Not caught; a programming/setup error |
| Unknown seat id | `IllegalArgumentException` | `Show.bookSeat()` (`Show.java:54-58`) | Not caught |
| Duplicate seat / show id | `IllegalArgumentException` | `Show.addSeat()` / `Theatre.addShow()` (`Show.java:39`, `Theatre.java:21`) | Not caught; setup error |
| Seat occupied and wait times out | `SeatAlreadyBookedException` (checked) | `Show.bookSeat()` (`Show.java:71-73`) | `Customer.run()` (`Customer.java:45-47`) |
| Waiting thread interrupted | `InterruptedException` | `wait()` (`Show.java:75`) | `Customer.run()` (`Customer.java:48-52`); interrupt flag restored |
| CSV write fails | `IOException` | `FileStorage.saveBookings()` | `BookingService.persistAllBookings()` (`:74-82`) → warning |
| Receipt write fails | `IOException` | `ReceiptGenerator` | `recordBooking` / `recordCancellation` → warning |

The checked exception is used for the expected business failure ("seat taken
and you timed out"); `IllegalArgumentException` is reserved for caller bugs.

## 11. End state after a clean run

Assuming `data/` and `receipts/` are empty before the run:

```text
data/bookings.csv
  BK-1001  Customer-N  SHOW-RACE  L1   CONFIRMED
  BK-1002  Alice       SHOW-WAIT  W1   CANCELLED
  BK-1003  Bob         SHOW-WAIT  W1   CONFIRMED

receipts/
  BK-1001.txt
  BK-1002.txt
  BK-1002-cancelled.txt
  BK-1003.txt
```

`Main.printGeneratedFiles()` (`Main.java:177-191`) lists the actual directory
contents and prints Bob's receipt. If old receipt files exist, they are listed
too, because the program does not delete `receipts/` on startup.

## 12. Full end-to-end call graph

```text
Main.main
 ├── printBanner
 ├── new Theatre
 ├── createMainShow
 │    ├── new Show
 │    └── show.addSeat × 6
 ├── theatre.addShow
 ├── new BookingService
 │    ├── new FileStorage("data")      -> creates data/
 │    └── new ReceiptGenerator("receipts") -> creates receipts/
 ├── printSeatCatalogue
 │    └── show.getSeatsSnapshot + seat.calculatePrice
 ├── demonstrateLastSeatRace
 │    ├── new Show + addSeat(L1) + theatre.addShow
 │    ├── 6 × { new Customer; new Thread; start; await latch }
 │    ├── latch.countDown
 │    ├── Customer.run -> BookingService.book -> Show.bookSeat
 │    │    ├── winner: Seat.book -> new Booking -> recordBooking -> CSV + receipt
 │    │    └── losers: waitlist.add -> wait(timeout) -> exception -> finally remove
 │    └── join all -> print results
 ├── demonstrateWaitlist
 │    ├── new Show + addSeat(W1) + theatre.addShow
 │    ├── service.book(Alice)
 │    ├── Thread(Bob).start -> Show.bookSeat -> waitlist.add -> wait
 │    ├── service.cancel(Alice)
 │    │    ├── Show.cancelBooking -> cancel + release + notifyAll
 │    │    └── recordCancellation -> CSV + cancelled receipt
 │    ├── Bob wakes -> Seat.book -> new Booking -> recordBooking
 │    └── join Bob -> print bookings -> return Bob's booking
 └── printGeneratedFiles
      ├── print data/bookings.csv path
      ├── list receipts/
      └── read and print Bob's receipt
```
