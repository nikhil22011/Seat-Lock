# SeatLock 🎟️

**Real-time event ticket booking where double booking is impossible.**

Pick seats on a live seat map, lock them for 5 minutes while you pay, and get a signed QR ticket that works exactly once at the gate. When anyone else holds or buys a seat, it changes colour on every open screen instantly.

Built with **Spring Boot 3 · Java 21 · Spring Security (JWT) · JPA · WebSockets (STOMP) · React 19 · TypeScript · TanStack Query · Tailwind CSS**.

---
CREDS:

user@seatlock.dev	Ticket buyer
user2@seatlock.dev	Second buyer
organizer@seatlock.dev	Event organizer

(the password is password123 for al

## Features

| For ticket buyers | For organizers |
|---|---|
| Browse and search upcoming events by city | Create events with multi-tier seating (auto-generated seat map) |
| Live seat map: see holds and sales as they happen | Live dashboard: revenue, tickets sold, check-ins |
| All-or-nothing seat holds with a 5-minute countdown | Gate scanner (phone camera QR scan or paste code) |
| Test-mode payment, with a "declined card" demo | Forged or reused tickets are rejected instantly |
| QR e-tickets under "My tickets" | |

## The hard problems (and how they're solved)

### 1. Two people click the same seat at the same millisecond
Seats are claimed with a single **conditional UPDATE**, an atomic compare-and-set in the database:

```sql
UPDATE seats SET status='HELD', booking_id=?, hold_expires_at=?
 WHERE id IN (...) AND event_id=?
   AND (status='AVAILABLE' OR (status='HELD' AND hold_expires_at < now()))
```

The database row-locks each seat during the update. The second transaction re-checks the `WHERE` after the first commits and matches **0 rows**. The service compares *rows updated* with *seats requested*, and on any mismatch it rolls back the whole transaction. You get **all your seats or none**.

> ✅ Proven by `SeatHoldConcurrencyTest`: **50 threads** race for one seat and exactly **1** wins. Overlapping multi-seat requests never leave a partial hold.

No distributed lock service is needed. The database is the single source of truth, so a Redis outage can't cause double selling.

### 2. Someone double-clicks "Pay" (or the network retries)
Every payment carries an **`Idempotency-Key`** header, stored under a unique constraint. The booking row is locked (`SELECT … FOR UPDATE`) *before* checking for a previous attempt, so even simultaneous duplicates are serialized. The duplicate gets the stored result back and is **never charged twice**.
> ✅ `BookingLifecycleTest.doubleClickOnPay_chargesOnce`: 10 concurrent identical requests produce 1 payment.

### 3. Users abandon checkout
Holds carry an expiry. A scheduled sweeper (`HoldExpiryJob`) releases overdue holds in small per-booking transactions and broadcasts the freed seats. An expired hold is also claimable immediately (see the SQL above), so seats never get stuck waiting for the job.

### 4. Payment vs. expiry race
Pay, cancel and expire all take a **pessimistic lock on the booking row** first, so they can't interleave. If a hold expires in the instant between the check and the seat update, the seat count won't match and the payment rolls back. With a real gateway, that's where you'd issue a refund.

### 5. Real-time updates that never lie
Seat changes are published as Spring events and broadcast over STOMP **only after the transaction commits** (`@TransactionalEventListener(AFTER_COMMIT)`). A rolled-back hold is never shown to anyone. Messages are tiny deltas (`[{id, status}]`), and clients refetch the full map after a reconnect.

### 6. Fake and reused tickets
Ticket codes are `SL1.<bookingId>.<HMAC-SHA256 signature>`. Forged codes fail a **constant-time signature check** before touching the database. Check-in is an atomic `UPDATE … WHERE checked_in_at IS NULL`, so two gate scanners scanning the same ticket at once still admit only one person.

## Architecture

```mermaid
flowchart LR
    subgraph Browser["React SPA"]
        UI[Seat map / Checkout / Scanner]
        RQ[TanStack Query cache]
        WS[STOMP client]
    end
    subgraph API["Spring Boot"]
        SEC[Spring Security<br/>JWT filter]
        C[REST controllers]
        S[BookingService<br/>EventService]
        J[HoldExpiryJob<br/>@Scheduled]
        B[SeatBroadcaster<br/>AFTER_COMMIT]
        PG[PaymentGateway<br/>mock → Razorpay/Stripe]
    end
    DB[(PostgreSQL / H2)]

    UI -->|REST + Bearer JWT| SEC --> C --> S --> DB
    S --> PG
    J --> S
    S -. domain event .-> B
    B -->|/topic/events/id/seats| WS --> RQ --> UI
```

**Booking state machine:** `HELD → CONFIRMED` (paid) · `HELD → EXPIRED` (timer) · `HELD → CANCELLED` (user)

## Run it locally

**Prerequisites:** Java 21+, Maven, Node 20+. No database install needed: dev mode uses in-memory H2 with demo data.

```bash
# Terminal 1: API on http://localhost:8080
cd backend && mvn spring-boot:run

# Terminal 2: web app on http://localhost:5173
cd frontend && npm install && npm run dev
```

**Demo logins** (password `password123`), also one-click buttons on the login page:
- `user@seatlock.dev`: ticket buyer
- `user2@seatlock.dev`: second buyer (open in another browser to watch live updates)
- `organizer@seatlock.dev`: organizer (dashboard + check-in)

**Try the live demo:** open the same event in two browsers logged in as different users, and select a seat in one. Watch it turn striped in the other.

Useful URLs: Swagger UI `http://localhost:8080/swagger-ui.html` · H2 console `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:seatlock`)

### With Docker + PostgreSQL

```bash
docker compose up --build     # → http://localhost:3000
```

## Tests

```bash
cd backend && mvn test
```

| Test | What it proves |
|---|---|
| `SeatHoldConcurrencyTest` | 50-way race gives exactly 1 winner; overlapping requests are all-or-nothing |
| `BookingLifecycleTest` | Hold → pay → check-in; double-click pay charges once; declined card keeps hold; expiry frees seats; forged/reused tickets rejected; users can't touch others' bookings |
| `ApiFlowTest` | Full flow over HTTP with real JWTs and role checks (401/403) |

## API overview

| Method | Endpoint | Auth |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | public |
| GET | `/api/events`, `/api/events/{id}`, `/api/events/{id}/seats` | public |
| POST | `/api/events` | ORGANIZER |
| GET | `/api/organizer/events` | ORGANIZER |
| POST | `/api/events/{id}/holds` | USER |
| POST | `/api/bookings/{id}/pay` (header `Idempotency-Key`) | owner |
| DELETE | `/api/bookings/{id}` (cancel hold) | owner |
| GET | `/api/bookings/me` | USER |
| POST | `/api/checkin` | ORGANIZER (own events) |
| WS | `/ws` → subscribe `/topic/events/{id}/seats` | public |

Errors follow RFC 7807 (`application/problem+json`) with a stable `code` field, e.g. `SEAT_UNAVAILABLE`, `HOLD_EXPIRED`, `ALREADY_CHECKED_IN`.

## Project structure

```
backend/src/main/java/com/seatlock/
  auth/       JWT issue + login/register
  event/      events, seats, the atomic hold/confirm/release queries
  booking/    booking lifecycle, expiry job, check-in
  payment/    idempotent payments behind a PaymentGateway interface
  ticket/     HMAC-signed ticket codes
  realtime/   STOMP config + after-commit broadcaster
frontend/src/
  pages/      Home, Event (seat map + checkout), Tickets, Organizer, CreateEvent, CheckIn
  components/ SeatMap, Countdown, Layout, UI kit
  lib/        auth context, WebSocket hook, formatting
```

## Roadmap

- [ ] Real Razorpay/Stripe gateway + webhook confirmation and automatic refunds
- [ ] Flyway migrations instead of `ddl-auto`
- [ ] Redis pub/sub (or a STOMP broker relay) so WebSocket updates fan out across multiple API instances
- [ ] Rate limiting on hold endpoints (bot protection)
- [ ] Email tickets, waitlists when sold out
- [ ] Load test with k6/Gatling and publish results
