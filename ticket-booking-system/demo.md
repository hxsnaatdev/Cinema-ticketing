# Live Demo & Presentation Guide

A 10-minute walkthrough for presenting the project to a teacher. It includes
what to run, what to say, which files to show, expected output, and answers to
likely questions.

Files referenced:

- [`flow.md`](flow.md) — full control flow and thread flow
- [`decision.md`](decision.md) — design rationale and trade-offs
- `run.sh` / `run.bat` — launchers

---

## 1. Before class (5 minutes)

### Checklist

- [ ] JDK 17 or newer is installed: `java -version` and `javac -version`
- [ ] Project opens in a terminal at the project root
- [ ] Terminal font is large enough for the back of the room
- [ ] Old output is removed so the file list is clean
- [ ] A dry run has already succeeded
- [ ] GUI dry run: `./run.sh gui`, then open **Add Movie** once
- [ ] This guide, `flow.md`, and `decision.md` are open for reference

### Clean old output

Linux / macOS / Git Bash / WSL:

```bash
rm -rf data receipts out
```

Windows Command Prompt:

```bat
rmdir /s /q data receipts out 2>nul
```

Windows PowerShell:

```powershell
Remove-Item -Recurse -Force data, receipts, out -ErrorAction SilentlyContinue
```

> `data/`, `receipts/`, and `out/` are generated and gitignored. Deleting them
> is safe. Removing `receipts/` matters because the program never deletes old
> receipt files; otherwise the final file listing shows leftovers from earlier
> runs.

### Build and run

Linux / macOS / Git Bash / WSL:

```bash
chmod +x run.sh
./run.sh
```

Windows:

```bat
run.bat
```

The run takes roughly 5–8 seconds because of the deliberate wait timeouts.

---

## 2. The 10-minute script

### 0:00–0:45 — Opening

Say:

> This is a multithreaded cinema ticket booking system written in Java 17. It
> demonstrates object-oriented design, inheritance and polymorphism, custom
> exceptions, file handling, and thread synchronization. The program runs two
> scripted demos. The first is six customers racing for the last seat. The
> second is a customer waiting for a seat and being woken up when it is
> cancelled. There is also a Swing GUI for interactive use, including a
> runtime **Add Movie** action.

Point at the package structure:

```text
src/ticketbooking/
├── Main.java
├── model/
├── service/
└── exception/
```

Mention the split: `model` is the domain, `service` handles files and
receipts, `Main` runs the demo.

### 0:45–2:15 — OOP model and seat catalogue

Start the program. Point at the opening output:

```text
Theatre: PVR Manipal, Jaipur

Seat catalogue for "Interstellar" (SHOW-1):
  A1  Regular    Rs.  200.00
  A2  Regular    Rs.  200.00
  B1  Premium    Rs.  300.00
  B2  Premium    Rs.  300.00
  C1  Reclining  Rs.  400.00
  C2  Reclining  Rs.  400.00
Available seats: 6
```

Say:

> `Seat` is an abstract class. It holds the shared state: id, row, number,
> base price, and booked flag. It declares `calculatePrice()`, and each
> subclass overrides it: regular is 1×, premium is 1.5×, reclining is 2×.
> `Main` stores them all as `Seat` references, so the JVM picks the right
> price at runtime. That is polymorphism.

Show the code:

- `src/ticketbooking/model/Seat.java:12` — abstract class
- `Seat.java:41` — abstract `calculatePrice()`
- `RegularSeat.java:16`, `PremiumSeat.java:18`, `RecliningSeat.java:18` —
  overrides

Optional one-liner: "`Seat` is also encapsulated: `book()` and `release()` are
package-private, so only the model layer can change the booked state."

### 2:15–4:15 — Demo 1: last-seat race

Point at the log:

```text
--- DEMO 1: Last-seat race (synchronized prevents double booking) ---
[THREAD Customer-6] Customer-6 is trying to book seat L1...
...
[WAITLIST] Customer-1 joined the waitlist for seat L1 in "Midnight Premiere". Waiting: 1
...
[THREAD Customer-6] SUCCESS: Customer-6 booked seat L1 for Rs. 200.00 (Booking BK-1001)
[MAIN] Customers currently on the waitlist: 5
...
[RESULT] Confirmed bookings for seat L1: 1 (exactly 1 expected)
[RESULT] Waitlist after all threads finish: 0
```

Say:

> Six threads are released at the same instant by a `CountDownLatch`, so they
> all try to book the one seat `L1`. Exactly one wins. The others see the seat
> is booked, join the waitlist, and call `wait(2500)`. When the timeout expires
> they throw `SeatAlreadyBookedException`, and a `finally` block removes them
> from the waitlist, which is why the final waitlist size is zero.

Show the code:

- `Main.java:86-112` — latch and six threads
- `Show.java:51` — `synchronized Booking bookSeat(...)`
- `Show.java:60-64` — if booked, join waitlist
- `Show.java:66-76` — deadline, `while`, `wait(remaining)`
- `Show.java:77-79` — `finally` cleanup
- `Show.java:84-86` — claim the seat and create the booking

Killer sentence:

> The check "is the seat free?" and the claim "mark it booked" are in the same
> synchronized method, so there is no gap where two threads can both win. That
> is why the confirmed count is exactly one.

Note for the presenter: the winner is whichever thread the JVM schedules first.
In the sample output it is `Customer-6`; on another run it can be a different
customer. The count is always one.

### 4:15–6:15 — Demo 2: waitlist and notify

Point at the log:

```text
--- DEMO 2: Waitlist with wait() / notify() ---
[MAIN] Alice booked seat W1 (BK-1002). Bob will now wait for it.
[THREAD Bob-Waitlist] Bob is trying to book seat W1...
[WAITLIST] Bob joined the waitlist for seat W1 in "The Waitlist". Waiting: 1
[MAIN] Waitlist size before cancellation: 1
[MAIN] Alice cancels her booking now.
[SHOW SHOW-WAIT] Booking BK-1002 cancelled; seat W1 is free. Calling notifyAll()...
[THREAD Bob-Waitlist] SUCCESS: Bob booked seat W1 for Rs. 300.00 (Booking BK-1003)
[RESULT] Bookings for show SHOW-WAIT:
  BK-1002 | Alice | seat W1 | CANCELLED | Rs. 300.00
  BK-1003 | Bob | seat W1 | CONFIRMED | Rs. 300.00
[RESULT] Bob was woken by notifyAll() and booked seat W1.
```

Say:

> Alice books the only seat. Bob tries, sees it is taken, joins the waitlist,
> and blocks in `wait()`. `wait()` releases the `Show` monitor, so the program
> is not frozen. When Alice cancels, `cancelBooking()` changes the status,
> releases the seat, and calls `notifyAll()`. Bob wakes up, re-checks the
> seat, sees it is free, and books it. Alice's booking stays in the list with
> status `CANCELLED`, so history is preserved.

Show the code:

- `Main.java:141-158` — Alice books, Bob waits, Alice cancels
- `Show.java:91-104` — cancel, release, `notifyAll()`
- `Show.java:68-76` — the `while` loop re-check
- `Customer.java:37-52` — `run()` catches the two expected outcomes

Explain why `notifyAll()` and not `notify()`:

> Several customers can be waiting for different seats in the same show. `notify()`
> might wake the wrong one. `notifyAll()` wakes everyone, and each waiter
> re-checks its own seat in a `while` loop, so the correct waiter can proceed.

Note for the presenter: log lines may interleave slightly differently between
runs, but the final state is always Alice cancelled and Bob confirmed.

### 6:15–7:45 — Files and persistence

Run:

```bash
cat data/bookings.csv
```

Windows:

```bat
type data\bookings.csv
```

Expected shape:

```text
booking_id,customer_id,customer_name,show_id,show_title,seat_id,seat_type,amount,booked_at,status
BK-1001,C6,Customer-6,SHOW-RACE,Midnight Premiere,L1,Regular,200.00,...,CONFIRMED
BK-1002,C-ALICE,Alice,SHOW-WAIT,The Waitlist,W1,Premium,300.00,...,CANCELLED
BK-1003,C-BOB,Bob,SHOW-WAIT,The Waitlist,W1,Premium,300.00,...,CONFIRMED
```

Then:

```bash
ls receipts
cat receipts/BK-1003.txt
```

Windows:

```bat
dir receipts
type receipts\BK-1003.txt
```

Say:

> Every successful booking causes the whole CSV snapshot to be rewritten. It is
> written to a temporary file first and then atomically moved over the real
> file, so a reader never sees a half-written CSV. Booking and cancellation
> receipts are separate text files. The cancellation keeps the original
> receipt and adds a `-cancelled` version.

Show the code:

- `FileStorage.java:32-47` — snapshot + temp file + atomic move
- `Booking.java:67-89` — CSV row and escaping
- `ReceiptGenerator.java:28-40` — receipt file names
- `BookingService.java:50-72` — after booking/cancel side effects

### 7:45–9:00 — Concurrency summary and design choices

Draw or point at this:

```text
Show lock  ->  checks availability, joins waitlist, claims seat, cancels
Seat lock  ->  protects the booked flag as a second line of defence
Service    ->  serialises CSV/receipt writes after the Show lock is released
```

Say:

> The lock order is always `Show` then `Seat`; `Seat` never calls back into
> `Show`, so there is no lock-order deadlock. Booking ids use `AtomicLong`
> because six threads can create bookings at once. Status is `volatile` so
> readers see the latest value. And snapshots are copies, so the demo can read
> data without holding the lock.

Mention the main limitation honestly:

> Waiters are not served strictly first-come-first-served. After
> `notifyAll()`, whichever waiter is scheduled first wins. Adding fairness
> would need a fair lock or an explicit queue, which I left out to keep the
> `wait()` / `notifyAll()` lesson clear.

### 9:00–10:00 — Close and questions

One-sentence summary:

> The project keeps the booking critical section small and correct, uses
> `wait()` / `notifyAll()` for inter-thread communication, and separates
> domain logic from file I/O so each concept can be demonstrated and explained
> independently.

Then invite questions.

---

## 3. Short 3-minute version

If time is cut:

1. Run the program.
2. Point at the seat catalogue and say "abstract `Seat`, three subclasses,
   runtime polymorphism".
3. Point at Demo 1 result and say "six threads, one synchronized method, one
   winner, waiters time out and clean up in `finally`".
4. Point at Demo 2 result and say "`wait()` releases the lock, `notifyAll()`
   wakes Bob, Bob re-checks and books".
5. Show `data/bookings.csv` and one receipt.

Skip the live code walkthrough; refer to `flow.md` and `decision.md`.

---

## 4. Commands cheat sheet

| Action | Linux / macOS / Git Bash | Windows cmd |
| --- | --- | --- |
| Clean output | `rm -rf data receipts out` | `rmdir /s /q data receipts out` |
| Build + run | `./run.sh` | `run.bat` |
| Run GUI | `./run.sh gui` | `run.bat gui` |
| Run GUI (compiled) | `java -cp out ticketbooking.ui.GuiMain` | `java -cp out ticketbooking.ui.GuiMain` |
| Compile only | `javac --release 17 -d out $(find src -name '*.java')` | `run.bat` (compiles then runs) |
| Run compiled CLI | `java -cp out ticketbooking.Main` | `java -cp out ticketbooking.Main` |
| View CSV | `cat data/bookings.csv` | `type data\bookings.csv` |
| List receipts | `ls receipts` | `dir receipts` |
| View receipt | `cat receipts/BK-1003.txt` | `type receipts\BK-1003.txt` |
| Check Java | `java -version` | `java -version` |

---

## 5. Mapping to the course topics

| Topic | Where to show it |
| --- | --- |
| Classes and objects | `Theatre`, `Show`, `Seat`, `Customer`, `Booking` |
| Abstraction | `Seat` abstract class (`Seat.java:12`) |
| Inheritance | `RegularSeat` / `PremiumSeat` / `RecliningSeat` extend `Seat` |
| Polymorphism | `seat.calculatePrice()` dispatch (`Main.java:67`, `Show.java:85`) |
| Encapsulation | `Seat.book()` / `release()` package-private; snapshot getters |
| Custom exception | `SeatAlreadyBookedException` (`SeatAlreadyBookedException.java:7`) |
| Exception handling | `Customer.run()` catch blocks (`Customer.java:45-52`) |
| File handling | `FileStorage`, `ReceiptGenerator` |
| Multithreading | `Customer implements Runnable`; six threads in Demo 1 |
| Synchronization | `Show.bookSeat()`, `Seat.book()`, `BookingService` |
| Inter-thread communication | `wait()` / `notifyAll()` (`Show.java:75`, `:103`) |
| Generics / collections | `Map<String, Seat>`, `List<Booking>`, `AtomicLong` |
| GUI integration | `BookingFrame` calls `BookingService`; **Add Movie** builds a `Show` and seats |

---

## 6. Likely questions and answers

**Q: Why is `bookSeat()` synchronized on `Show` and not on `Seat`?**
A: The operation needs to check availability, add to the waitlist, claim the
seat, and append a booking atomically. The `Show` owns the seat map, the
waitlist, and the booking list, so one monitor covers the whole check-then-act
sequence. If only the seat were locked, the waitlist and booking list would
still need separate coordination.

**Q: Can two threads book the same seat?**
A: No. Both the availability check and the claim happen inside
`snchronized bookSeat()`. The losing threads see `seat.isBooked() == true` and
either wait or time out. Demo 1 prints a confirmed count of exactly one.

**Q: Why `while (seat.isBooked())` instead of `if`?**
A: `wait()` can return spuriously, a different waiter can take the seat first,
or the notification can be for another seat. The `while` loop re-checks the
real condition every time.

**Q: Why `notifyAll()` instead of `notify()`?**
A: Multiple customers can wait for different seats in the same show.
`notify()` might wake a customer whose seat is still occupied. `notifyAll()`
wakes everyone, and each one re-checks its own condition.

**Q: What happens if nobody cancels?**
A: The waiter's `wait(remaining)` times out, `SeatAlreadyBookedException` is
thrown, and the `finally` block removes the waitlist entry. Bob's timeout is
5,000 ms; the race customers use 2,500 ms.

**Q: Why does the waitlist go back to zero at the end of Demo 1?**
A: Each waiter removes its own `WaitlistEntry` in `finally` on timeout. If the
entry were not removed, the printed waitlist size would be stale.

**Q: Is the booking id safe to generate under concurrency?**
A: Yes. `Booking.SEQUENCE` is an `AtomicLong`; `incrementAndGet()` is atomic. A
plain `long++` could produce duplicates.

**Q: Why keep cancelled bookings?**
A: `Show.bookings` is the history. The CSV marks the row `CANCELLED`, and the
cancellation receipt is written alongside the original receipt, so the teacher
can see the full lifecycle of seat `W1`.

**Q: What happens if writing the CSV or receipt fails?**
A: `BookingService` catches the `IOException`, prints a warning, and keeps the
in-memory booking. The booking is not rolled back because the seat was already
successfully claimed.

**Q: Why write a temporary file and rename it?**
A: It prevents a half-written CSV from being observed. On file systems that
support it, `ATOMIC_MOVE` makes the replacement all-or-nothing; the code falls
back to a normal replace otherwise.

**Q: Is the code fair? Does the first waiter always win?**
A: No. `notifyAll()` wakes everyone and the JVM chooses who runs first. FIFO
fairness is a known limitation and is listed in `decision.md`.

**Q: Could this deadlock?**
A: The only nested lock order is `Show → Seat`. `Seat` never calls into
`Show`, and the service lock is acquired around persistence, not while holding
`Show`. Because the order is consistent, a lock-order deadlock is avoided.

**Q: Why is `Booking.status` volatile?**
A: It is written during cancellation and read by code that may not hold the
`Show` lock (for example, receipt generation and snapshot iteration).
`volatile` guarantees visibility of the latest value. It is a single flag, so
atomicity beyond visibility is not needed.

**Q: Why does the program not load the existing CSV?**
A: The CSV is an output artifact for the demo; the scenario is rebuilt from
scratch on each run. Loading it would require parsing and reconstructing the
shows and seats. This is listed as a future improvement.

**Q: Does it run on Windows?**
A: Yes. It uses `Path` and `Files` with no hard-coded separators and UTF-8
encoding. `run.sh` detects Git Bash/MSYS/Cygwin and uses `java.exe` /
`javac.exe`; `run.bat` covers native Command Prompt and PowerShell.

**Q: What can I do in the GUI?**
A: The GUI provides the same booking operations plus a live seat map, booking
and waitlist tabs, a receipt viewer, a six-customer race button, and **Add
Movie**. It calls the same `BookingService` as the CLI, so it is a second
client rather than a second implementation of the rules.

**Q: Does Add Movie persist?**
A: No. It creates a `Show` and its seats in memory for the current GUI session
and registers the show in the `Theatre`. The new show is not written to CSV or
reloaded on the next launch.

---

## 7. Optional live changes (only if the teacher asks)

Make one code change, recompile, and show the effect. Restore the file
afterwards. The GUI add-movie step is runtime-only.

1. **Shorter race timeout** — change `WAIT_TIMEOUT_MS` in `Main.java:24` from
   `2500` to `500`. The five losers fail sooner.
2. **Bob fails instead of waiting** — change Bob's timeout in `Main.java:148`
   from `5000` to `100` and cancel Alice later. Bob times out; the program
   throws `IllegalStateException` at the end because Bob did not get the seat.
3. **Disable the wake-up** — temporarily comment out `notifyAll()` in
   `Show.java:103`. Bob is not woken directly; his `wait(5000)` only returns
   when the timeout expires, so the success line appears several seconds late
   instead of immediately after the cancellation. The demo still succeeds, but
   it visibly shows why `notifyAll()` matters for responsiveness. Restore it
   immediately.
4. **Add a new seat type** — create `CoupleSeat extends Seat` with a 2.5×
   multiplier and add one to the catalogue. No booking code changes are
   needed, which demonstrates the open/closed benefit of the abstraction.
5. **Add a movie in the GUI** — run `./run.sh gui`, press **Add Movie**, enter
   a title, and leave the default mixed 3×4 layout. Press **OK**. The show
   selector switches to the new show, the seat map displays its 12 new seats,
   and the status bar reports the generated show id. Book one seat to show the
   same `BookingService` / `Show.bookSeat()` path used by the CLI. This is a
   runtime-only change; restart the GUI to discard it.

Recompile code changes with `./run.sh` or `run.bat`.

---

## 8. If the live demo fails

1. Do **not** debug in front of the class. Switch to the fallback.
2. Run the already-compiled classes:
   - Linux/macOS: `java -cp out ticketbooking.Main`
   - Windows: `java -cp out ticketbooking.Main`
3. If compilation is the problem, show the expected output below and continue
   with the code walkthrough.
4. If the terminal scrollback is lost, show `data/bookings.csv` and the
   receipt files, which are already on disk from the dry run.

### Expected output (abridged)

```text
==============================================================
 MULTITHREADED TICKET BOOKING SYSTEM - CSC2104 MINI PROJECT
==============================================================
Theatre: PVR Manipal, Jaipur

Seat catalogue for "Interstellar" (SHOW-1):
  A1  Regular    Rs.  200.00
  ...
Available seats: 6

--- DEMO 1: Last-seat race (synchronized prevents double booking) ---
... six threads try seat L1 ...
[WAITLIST] ... Waiting: 5
[THREAD Customer-X] SUCCESS: ... (Booking BK-1001)
[MAIN] Customers currently on the waitlist: 5
... five FAILED lines after timeout ...
[RESULT] Confirmed bookings for seat L1: 1 (exactly 1 expected)
[RESULT] Waitlist after all threads finish: 0

--- DEMO 2: Waitlist with wait() / notify() ---
[MAIN] Alice booked seat W1 (BK-1002). Bob will now wait for it.
[WAITLIST] Bob joined the waitlist for seat W1 ...
[MAIN] Waitlist size before cancellation: 1
[MAIN] Alice cancels her booking now.
[SHOW SHOW-WAIT] Booking BK-1002 cancelled; seat W1 is free. Calling notifyAll()...
[THREAD Bob-Waitlist] SUCCESS: Bob booked seat W1 for Rs. 300.00 (Booking BK-1003)
[RESULT] Bookings for show SHOW-WAIT:
  BK-1002 | Alice | seat W1 | CANCELLED | Rs. 300.00
  BK-1003 | Bob | seat W1 | CONFIRMED | Rs. 300.00
[RESULT] Bob was woken by notifyAll() and booked seat W1.

--- Generated files ---
Bookings CSV : .../data/bookings.csv
Receipts dir : .../receipts
...
```

The exact interleaving of `[FILE]`, `[RECEIPT]`, and `[THREAD]` lines can vary.
The winner in Demo 1 can be any of the six customers. The final results are
deterministic.

---

## 9. Presenter notes

- Say "one winner" rather than naming a customer for Demo 1; the winner is not
  deterministic.
- When showing code, keep to the anchor lines in this guide. Do not scroll
  through all 980 lines.
- If asked about a line number, use the references in `flow.md`; they are
  accurate for the current source.
- Mention the limitation about fairness before the teacher asks. It shows
  awareness and makes the design discussion stronger.
- Keep `decision.md` open for "why did you..." questions.
