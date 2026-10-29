# Design Decisions

This file explains **why** the code is shaped the way it is. Each section states
the decision, the reasoning, and the trade-off accepted. The goal was a small
project that clearly demonstrates the CSC2104 topics: classes and objects,
abstraction, inheritance, polymorphism, encapsulation, exceptions, file
handling, multithreading, synchronization, and inter-thread communication.

## 1. Layered packages instead of one big class

**Decision:** split the code into `model`, `service`, `exception`, and a
top-level demo class.

**Why:**

- `model` contains business objects and rules. It knows nothing about files,
  receipts, or `System.out` formatting beyond demo-friendly messages.
- `service` contains application orchestration and I/O. `BookingService`,
  `FileStorage`, and `ReceiptGenerator` can be changed without touching the
  booking rules.
- `exception` gives the custom checked exception its own visible home.
- `Main` is only glue for the demonstration.

**Trade-off:** more files and a little more indirection than a single-class
solution. For a teaching project this is worth it because each responsibility
is easy to point at during the presentation.

**Alternative considered:** one `Main.java` with nested classes. Rejected
because it hides the OOP structure the project is meant to show.

## 2. `Seat` as an abstract class with three subclasses

**Decision:** `Seat` is abstract; `RegularSeat`, `PremiumSeat`, and
`RecliningSeat` extend it and override `getSeatType()` and
`calculatePrice()`.

**Why:**

- A seat always has an id, row, number, base price, and booked state. That
  shared state belongs in one place (`Seat`).
- The pricing rule is the part that varies. Making it abstract forces every
  new seat type to define its own price.
- The demo can store everything as `List<Seat>` and still get the correct
  price through dynamic dispatch. That is the clearest possible demonstration
  of polymorphism.
- Adding a fourth seat type later (for example, a couple seat) means adding one
  subclass and changing no booking logic.

**Trade-off:** three very small subclasses. Some might use an enum plus a
price multiplier instead, but an enum would not demonstrate inheritance and
polymorphism.

**Alternative considered:** an interface `PricedSeat`. Rejected because the
subclasses also share concrete state and the `booked` flag, so an abstract
class is a better fit.

## 3. `Show` owns the booking critical section

**Decision:** `Show.bookSeat()` is the synchronized method that checks
availability, claims the seat, and creates the booking.

**Why:**

- The operation is a **check-then-act** sequence. If the availability check and
  the claim happened in separate locks, two threads could both see the seat as
  free and both book it. Keeping the whole sequence under one monitor makes it
  atomic.
- The decision also needs the show's waitlist and booking list. Putting the
  lock on `Show` keeps all related state under one lock instead of spreading
  the policy across `Seat`, `Booking`, and the service.
- The `Show` object is the natural contention point: customers booking the same
  screening must be serialized; customers booking different screenings do not
  need to block each other.

**Trade-off:** all operations on one show are serialized, including snapshot
reads. This would be a bottleneck for a very large, high-throughput system, but
it is negligible for this project and much easier to reason about.

**Alternative considered:** a `ReentrantLock` inside `Show`. It offers
`tryLock`, fairness, and interruptible lock acquisition, but `wait()` /
`notifyAll()` require an intrinsic monitor anyway. Using `synchronized` keeps
one locking mechanism instead of two.

## 4. `synchronized` methods rather than explicit locks

**Decision:** use the `synchronized` keyword on `Show`, `Seat`, `Theatre`,
`BookingService`, and `FileStorage` methods.

**Why:**

- It is the simplest correct tool for this scale.
- The monitor is released automatically on normal return and on exception, so
  there is no risk of forgetting an `unlock()`.
- `wait()` and `notifyAll()` are monitor operations; using them with
  `synchronized` is the idiomatic Java pattern.
- The code stays readable for a reader who is learning Java concurrency.

**Trade-off:** no `tryLock`, no fairness policy, and no separate read/write
locking. `ReentrantReadWriteLock` could allow concurrent reads, but the
demonstration does not need that.

## 5. `wait()` / `notifyAll()` instead of polling or `sleep()`

**Decision:** a customer who wants an occupied seat joins the waitlist and
calls `wait(remaining)`. Cancellation calls `notifyAll()`.

**Why:**

- Polling with `Thread.sleep()` in a loop wastes CPU and always adds latency.
  `wait()` blocks the thread until something changes and releases the `Show`
  monitor while blocked, so other threads can keep working.
- `notifyAll()` rather than `notify()` because several customers may be waiting
  for different seats in the same show. `notify()` could wake a customer whose
  seat is still occupied and leave the correct one asleep. Waking all of them
  is slightly less efficient but correct.
- The wait loop is `while (seat.isBooked())`, not `if`. This handles:
  - spurious wake-ups allowed by the JVM,
  - another waiter winning the seat first,
  - a notification caused by a *different* seat being cancelled.

**Trade-off:** after a cancellation, all waiters wake and compete, so the
winner is not guaranteed to be the first in line. That fairness limitation is
documented in section 16.

**Alternative considered:** a `BlockingQueue` per seat or a
`Condition`/`ReentrantLock` design. Both are valid, but `wait()` /
`notifyAll()` is exactly the inter-thread communication topic the project is
supposed to demonstrate.

## 6. Wait timeout with a recomputed deadline

**Decision:** `bookSeat()` accepts `waitTimeoutMs`, computes
`deadline = now + waitTimeoutMs`, and calls `wait(remaining)` in a loop.

**Why:**

- Without a timeout, a demo thread could wait forever if nobody cancels, and
  the program would hang during a presentation.
- `wait(remaining)` avoids oversleeping after a spurious wake-up.
- `Math.max(0, waitTimeoutMs)` lets a caller pass `0` to mean "do not wait". In
  that case the customer is added to the waitlist, sees `remaining <= 0`, and
  fails immediately. Alice's synchronous booking uses this path to show a
  no-wait call, while Bob uses 5,000 ms.

**Trade-off:** a real system would probably use a configurable policy or a
queue with expiration. The hard-coded demo values are tuned for a visible
demonstration.

## 7. Waitlist entry removed in a `finally` block

**Decision:** each waiter adds a `WaitlistEntry` before waiting and removes it
in `finally`.

**Why:**

- The waitlist is observable, and the demo prints its size. Leaving timed-out
  or interrupted entries behind would make that output misleading.
- `finally` runs on every exit path: success, timeout, and interruption.
- It is a small example of resource cleanup that is easy to explain.

**Trade-off:** the entry is removed only when the waiter reacquires the monitor
and leaves `bookSeat()`. That is fine because the waiter is still logically
waiting until then.

## 8. `Seat` also synchronizes its own state

**Decision:** `Seat.isBooked()`, `Seat.book()`, and `Seat.release()` are
synchronized, and `book()`/`release()` are package-private.

**Why:**

- `Show` is the primary gate, but `Seat` is also read directly by external
  code (`isBooked()` in `Main`, `getAvailableSeatCount()`). Giving the seat its
  own monitor makes each flag read/write safe on its own.
- It is defence in depth: even if another model class used `Seat` directly in
  the future, the flag would still be protected.
- Package-private mutators keep the state change inside the model layer, which
  is encapsulation: callers cannot mark a seat booked without going through
  `Show`.

**Trade-off:** the same logical operation takes two nested locks
(`Show` then `Seat`). That is safe here because the lock order is always
`Show → Seat` and `Seat` never calls into `Show`. It does add a small amount of
conceptual overhead, so the ordering rule must be kept in mind.

## 9. Booking is a separate object from Seat

**Decision:** `Booking` stores the customer, show, seat, amount, timestamp, and
status. The seat only stores its current `bookedBy`.

**Why:**

- A seat can be booked, cancelled, and booked again. Keeping only the current
  customer on the seat would lose history.
- `Show.bookings` keeps every attempt that became a booking, including
  cancelled ones, which is exactly what the CSV and cancellation receipt need.
- `BookingStatus` as an enum makes the two states explicit and type-safe.

**Trade-off:** more memory per booking and a little more code than a boolean
on the seat. The history is worth it.

## 10. `AtomicLong` for booking ids

**Decision:** `Booking.SEQUENCE` is a `static final AtomicLong` starting at
1000, incremented in the constructor.

**Why:**

- Six threads can create bookings concurrently. A plain `long++` would race.
- `incrementAndGet()` is atomic without holding the `Show` lock, so id creation
  is safe even if a future code path creates a booking outside `bookSeat()`.
- The sequence is static, so ids are unique across all shows.

**Trade-off:** ids are only unique within one JVM run and are not sortable by
time. For this project, readable sequential ids (`BK-1001`, `BK-1002`, ...) are
more useful for the demonstration than UUIDs.

**Alternative considered:** `UUID.randomUUID()`. Rejected because long,
unreadable ids make the receipts and console output harder to follow.

## 11. `volatile` booking status

**Decision:** `Booking.status` is `volatile`.

**Why:**

- Status is written while holding the `Show` lock but read from places that may
  not hold that lock (`Main` iterating a snapshot, receipt generation).
  `volatile` guarantees that a reader sees the latest write.
- It is a single, independent flag; no compound atomic operation is needed, so
  `volatile` is sufficient and cheaper than making every status access
  synchronized.

**Trade-off:** `volatile` does not make a sequence of operations atomic. Here
that is acceptable because the only later writer is the synchronized
cancellation path (the constructor initialises it to `CONFIRMED`).

## 12. Checked exception for an occupied seat

**Decision:** `SeatAlreadyBookedException extends Exception` (checked) and is
thrown on timeout. Invalid ids use `IllegalArgumentException`.

**Why:**

- A seat being taken is an expected business outcome, not a bug. Making it a
  checked exception forces `Customer.run()` to handle it explicitly and keeps
  the failure visible in the API.
- Invalid ids, duplicate ids, and null arguments indicate a programming or
  setup mistake, so unchecked exceptions are appropriate.

**Trade-off:** checked exceptions add `throws` clauses and catch blocks. For a
teaching project, that is a feature: it shows the difference between expected
and unexpected failures.

## 13. Snapshot getters instead of exposing collections

**Decision:** `getSeatsSnapshot()`, `getBookingsSnapshot()`,
`getWaitlistSnapshot()`, and `getConfirmedBookings()` return new `ArrayList`
instances.

**Why:**

- Returning the internal list would let callers modify it without the lock and
  could cause `ConcurrentModificationException`.
- The demo and receipts can iterate safely after the lock is released.
- Returning a copy keeps encapsulation: the model controls its own state.

**Trade-off:** copying costs a little time and memory. The collections are tiny
here, and correctness/readability matter more.

## 14. `BookingService` as a facade

**Decision:** `Customer` calls `BookingService`, not `Show` or `FileStorage`
directly.

**Why:**

- `Customer` should express intent ("book this seat"), not orchestrate storage
  and receipt writing.
- Side effects happen in one place, in a consistent order: confirm the booking
  first, then persist, then write the receipt.
- The model stays free of file-system dependencies, so it could be reused with
  a database or a GUI later.

**Trade-off:** one extra layer of indirection. It pays off because the
persistence code is not duplicated in every caller.

## 15. Persist a full snapshot with an atomic move

**Decision:** every save rewrites `data/bookings.csv` from the complete list,
writing to a temporary file first and then replacing the target.

**Why:**

- The dataset is small, so rewriting is cheap and simple.
- A snapshot is always self-consistent: a cancellation changes the row in
  place instead of requiring an append/update/delete log format.
- Writing to a temporary file prevents a reader (or a crash) from seeing a
  half-written CSV. `ATOMIC_MOVE` makes the replacement all-or-nothing where
  the file system supports it; the fallback keeps the code portable.
- It demonstrates standard file handling with `Files`, `Path`, and UTF-8.

**Trade-off:** O(n) work per booking, which would not scale to a large system.
A database or append-only log would be better at scale, but that is outside the
scope and would hide the file-handling requirement.

**Alternative considered:** appending one line per booking. Rejected because
cancellations need to modify an existing row, and an append-only format would
require a second file or a replay step.

## 16. No fairness guarantee

**Decision:** accept that the waiter which gets a freed seat is whichever thread
the JVM schedules first after `notifyAll()`.

**Why:**

- Implementing FIFO fairness would require an explicit queue with a
  `Condition` per position or a fair `ReentrantLock`. That would obscure the
  simple `wait()` / `notifyAll()` lesson.
- The demo's point is "the wake-up happens and exactly one winner is
  guaranteed", not "the earliest waiter always wins".

**Trade-off:** a waiter can starve if other threads keep winning. This is
documented as a limitation rather than hidden.

## 17. Demo output goes straight to `System.out`

**Decision:** the model and services print progress with `System.out.printf`.

**Why:**

- The project is a live demonstration. Visible, labelled output
  (`[WAITLIST]`, `[FILE]`, `[RECEIPT]`, `[THREAD ...]`) makes the concurrency
  behaviour obvious to an audience.
- It avoids introducing a logging framework dependency.

**Trade-off:** in production this would be replaced with a logger, and the
model should not print. For a classroom demo, the observability is the point.

## 18. Two separate shows for the two demos

**Decision:** Demo 1 uses `SHOW-RACE` with one seat; Demo 2 uses `SHOW-WAIT`
with one premium seat. The catalogue show `SHOW-1` is only for display.

**Why:**

- Each demo isolates one concept: contention on a single seat, then
  wait/notify on a cancellation.
- Separate shows prevent the waitlist from Demo 1 and the bookings from Demo 2
  from interfering with each other's printed results.
- The catalogue show demonstrates pricing without being mutated by the demos.

**Trade-off:** a little more setup code. The isolation makes the output much
easier to explain.

## 19. `CountDownLatch` start gate in Demo 1

**Decision:** all six customer threads block on a latch that `Main` releases
once.

**Why:**

- Without a gate, the first thread might finish before the sixth starts, which
  weakens the race demonstration.
- A latch is the standard, simple way to maximize contention and make the
  demonstration repeatable.

**Trade-off:** none of practical significance for this project.

## 20. Timing values tuned for presentation

**Decision:** 2,500 ms wait for the race, 5,000 ms for Bob, a 400 ms snapshot
in Demo 1, and a 1,000 ms pause before cancellation in Demo 2.

**Why:**

- The race needs enough time to show five waiters before they time out.
- Bob needs enough time for the audience to see him waiting before Alice
  cancels.
- The values keep the whole run under roughly ten seconds, which is suitable
  for a classroom demo.

**Trade-off:** the values are not configurable and are not production defaults.
They are deliberately chosen for visibility.

## 21. Cross-platform file handling and launch scripts

**Decision:** use `Path`/`Files` with `UTF-8`, avoid hard-coded path
separators, and provide both `run.sh` and `run.bat`.

**Why:**

- `Path.of("data")`, `resolve()`, and `Files` are portable across Linux, macOS,
  and Windows.
- UTF-8 avoids locale-dependent CSV and receipt corruption.
- `run.sh` works on Unix-like systems and Windows shells such as Git Bash/WSL
  and detects `java.exe`/`javac.exe`; `run.bat` covers native Command Prompt
  and PowerShell.
- Both scripts fail fast with a clear message if a JDK is missing.

**Trade-off:** two launchers must be kept in sync. The scripts are short enough
that this is low risk.

## 22. No database, GUI, or input loop

**Decision:** the program is a deterministic console demo with in-memory state
and file output.

**Why:**

- The assignment is about OOP and concurrency, not UI or database design.
- A fixed scripted scenario makes the race and the waitlist reproducible and
  easy to grade.
- File output satisfies the file-handling requirement without adding setup
  complexity for the teacher.

**Trade-off:** the program is not an interactive booking system and does not
reload previous bookings on startup. `data/bookings.csv` is an output artifact
for this run, not a persistent source of truth.

## Known limitations / possible improvements

| Limitation | Possible improvement |
| --- | --- |
| Waiters are not served FIFO | Fair lock or explicit queue with conditions |
| One `Show` lock serializes all seat operations | Per-seat locks or a seat-allocation service |
| Locks only work inside one JVM | Database transactions or a distributed lock |
| CSV is rewritten on every booking | Append-only log or a real database |
| Existing CSV is not loaded on startup | Add a loader and rebuild shows/bookings |
| Waitlist is not persisted | Persist waitlist entries alongside bookings |
| `System.out` used for logging | `java.util.logging` or SLF4J |
| Receipts from earlier runs are not cleaned | Clear `receipts/` in the launcher or use a run id |
| No automated tests | Add JUnit tests for pricing, timeout, cancellation, and CSV escaping |
| Show time is not enforced | Reject bookings after the show has started |

## Summary

The design deliberately chooses **clarity over scalability**. One monitor per
show, one wait/notify waitlist, and a full-snapshot CSV make the code easy to
explain and easy to verify, while still being correct for the scenarios the
project demonstrates. The main correctness rules are:

1. The availability check and the seat claim are in the same synchronized
   method.
2. `wait()` is always inside a `while` loop that re-checks the real condition.
3. `notifyAll()` is called after the seat is actually released, while holding
   the same monitor the waiters used.
4. The lock order is always `Show → Seat`, never the reverse.
