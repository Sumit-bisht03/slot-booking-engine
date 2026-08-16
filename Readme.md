# Slotify — High-Concurrency Slot Reservation & Booking Engine

A Calendly + Eventbrite–style booking platform built for a 10-day hackathon, focused on the hard distributed-systems problem underneath any booking product: **guaranteeing that a slot can never be double-booked, even under heavy concurrent load, network retries, or partial infrastructure failure.**

Every pattern below exists to close a specific failure mode a naive booking API would hit in production — not as a checklist exercise.

---

## Architecture Overview

Built as a **modular monolith** rather than microservices. The patterns being demonstrated here — distributed locking, idempotency, transactional outbox, retry/DLQ, rate limiting, caching — are infrastructure-level concerns that apply identically regardless of service boundaries. Splitting into microservices for a solo 10-day build would trade implementation time for deployment/networking plumbing without adding to what's being judged.

That said, the app is deliberately organized around a seam that *would* let it split cleanly later: bookings are persisted and events are published to RabbitMQ via the outbox pattern, and notification handling is a separate consumer that only talks to the rest of the system through that queue. That boundary is exactly where a future `notification-service` would be carved out.

```
Client
  |
  v
[ Rate Limiter (Bucket4j + Redis) ] -- 429 if exceeded
  |
  v
[ BookingController ]
  |
  v
[ BookingService ] -- idempotency short-circuit (fast path, no lock)
  |
  v
[ Redisson Distributed Lock : slot-lock:{slotId} ]
  |
  v
[ BookingTransactionService ]  (single DB transaction)
  |   +- re-check idempotency (race-safe)
  |   +- verify slot still AVAILABLE
  |   +- flip slot -> BOOKED           (optimistic @Version check)
  |   +- insert Booking row
  |   +- insert OutboxMessage row (PENDING)
  |
  v
[ OutboxPublisherScheduler ] (polls every 3s)
  |
  v
[ RabbitMQ: booking.exchange -> booking.events.queue ]
  |
  v
[ NotificationConsumer ] -- retries on transient failure
  |
  v (exhausted retries)
[ DLX -> booking.events.dlq ] -> [ DeadLetterConsumer ]
```

---

## Why Each Pattern Exists

### 1. Distributed Locking (Redisson)
**Problem it solves:** hundreds of users hitting the same slot in the same millisecond.
Every booking attempt acquires a Redisson `RLock` on `slot-lock:{slotId}` (`tryLock(waitTime=3s, leaseTime=5s)`) before any slot state is touched. This serializes concurrent attempts on the *same* slot across every instance of the app — not just within one JVM.

**Defense in depth:** the lock is the first line of defense, not the only one. `Slot` also carries a JPA `@Version` column, so even in the hypothetical case where the lock is bypassed or its TTL expires early, the database itself rejects a conflicting concurrent write via an optimistic-locking exception. Two independent guarantees, not one.

### 2. Idempotency (`X-Idempotency-Key`)
**Problem it solves:** a client's network retry, or a double-click, creating two bookings (and potentially two charges) for what the user experienced as one action.
Every request carries an idempotency key. It's checked **twice**: once before acquiring the lock (fast path — if we've already processed this key, return the cached result immediately, no lock needed), and once again *inside* the lock (closes the race where two requests with the same key both pass the outer check before either commits).

### 3. Transactional Outbox Pattern
**Problem it solves:** the classic "dual write" problem — saving a booking to Postgres and publishing a `BookingConfirmed` event to RabbitMQ are two separate systems. If you write to both independently, a crash between the two can silently lose the event (booking exists, nobody's ever notified) or publish an event for a booking that never actually committed.
Instead, the booking row and the outbox event row are written **in the same database transaction**. A separate `OutboxPublisherScheduler` polls for `PENDING` outbox rows every 3 seconds and publishes them to RabbitMQ. If RabbitMQ is down, the publish attempt fails, the row stays `PENDING`, and it's retried on the next poll — the booking itself is never at risk, and no confirmation is ever silently dropped.

### 4. Retry & Dead-Letter Handling
**Problem it solves:** transient failures (SMTP down, broker hiccup) shouldn't cause a permanently lost notification, but a permanently broken message also shouldn't retry forever.
Two independent retry layers exist:
- **Outbox to RabbitMQ publish:** on failure, `retryCount` increments and `nextRetryAt` is set using exponential backoff (`2^retryCount x 2` seconds). After `MAX_RETRIES` (3), the row is marked `FAILED` instead of retrying indefinitely.
- **Notification consumer to downstream delivery:** `NotificationConsumer` simulates delivery attempts; on transient failure it throws and lets RabbitMQ redeliver. After 3 attempts, it explicitly throws `AmqpRejectAndDontRequeueException`, which — combined with the queue's `x-dead-letter-exchange` configuration — routes the message to `booking.events.dlq` instead of retrying forever. `DeadLetterConsumer` picks it up for inspection/alerting.

### 5. Rate Limiting (Bucket4j + Redis)
**Problem it solves:** bot attacks and inventory sweeps hammering the booking endpoint.
A token bucket per client (keyed by `X-User-Id`, falling back to IP) is enforced via Bucket4j's Redisson integration — state lives in Redis, not JVM memory, so the limit is genuinely shared across app instances and survives restarts. Exceeding the limit returns `429` with a `Retry-After` hint.

### 6. Caching (Redis, write-through invalidation)
**Problem it solves:** availability-grid reads (`GET /slots/available`, `GET /slots/host/{id}`) happen far more often than slots change state — every page load re-checks availability.
Both are `@Cacheable` (Redisson-backed cache manager). Cache entries are explicitly evicted the moment a booking or cancellation changes the underlying data (`@CacheEvict` on both the booking-transaction path and cancellation), so reads are fast but never stale by more than one transaction.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Core framework | Spring Boot 4.1 (Java 25) |
| Database | PostgreSQL 16 |
| Distributed lock / cache / rate-limit store | Redis 7 (via Redisson) |
| Message broker | RabbitMQ 3.13 (with DLX/DLQ topology) |
| Rate limiting | Bucket4j (Redisson extension) |
| API docs | springdoc-openapi (Swagger UI) |

---

## Known Limitations (deliberate scope calls, not oversights)

- **Auth is a placeholder, not real authentication.** `X-User-Id` is a trusted header with no signature or session backing it — it identifies *what a caller claims*, not a verified identity. In production this would be a validated JWT. Flagging this explicitly rather than pretending it's solved.
- **No multi-seat/capacity events.** The current model is 1:1 per slot (Calendly-style). Eventbrite-style multi-capacity events (`capacity` > 1 per slot) would extend `Slot` with a `capacity`/`bookedCount` pair rather than a boolean-ish status — noted as a natural next step, not implemented here.
- **In-memory attempt tracking in `NotificationConsumer`** resets on restart and doesn't share state across multiple consumer instances. A quorum-queue-based `x-delivery-count` (RabbitMQ-native, Raft-replicated) would solve this properly; noted as a possible upgrade.

---

## Setup

### Prerequisites
- Java 25
- Docker & Docker Compose

### 1. Start infrastructure
```bash
docker compose up -d
```
This starts Postgres (`5432`), Redis (`6379`), and RabbitMQ (`5672`, management UI on `15672`).

### 2. Run the application
```bash
./mvnw spring-boot:run
```
On first run, Hibernate creates the schema (`ddl-auto: update`) and `DataSeeder` populates sample hosts/clients/slots.

### 3. Explore the API
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- RabbitMQ management console: `http://localhost:15672` (user: `slotify_mq` / password: `slotify_mq_password`)

---

## Core API Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/slots/available` | List all available slots (cached) |
| `GET` | `/api/v1/slots/host/{hostId}` | List a host's available slots (cached) |
| `POST` | `/api/v1/bookings` | Create a booking. Requires `X-Idempotency-Key` header or body field. Rate-limited. |
| `GET` | `/api/v1/bookings/{bookingId}` | Fetch a booking |
| `PUT` | `/api/v1/bookings/{bookingId}/cancel` | Cancel a booking. Requires `X-User-Id` header. Releases the slot and evicts cache. |

---

## Concurrency Proof

To verify the double-booking guarantee, fire N concurrent `POST /api/v1/bookings` requests at the same `slotId`:
```bash
# example with a simple loop; swap in k6/JMeter for a real load test
for i in $(seq 1 50); do
  curl -s -X POST http://localhost:8080/api/v1/bookings \
    -H "Content-Type: application/json" \
    -H "X-Idempotency-Key: $(uuidgen)" \
    -d '{"slotId":1,"clientId":2,"idempotencyKey":"'"$(uuidgen)"'"}' &
done
wait
```
Expected result: **exactly one** `201 Created`; the rest return `409 Conflict` (`SlotAlreadyBookedException`).

## Outbox Durability Proof

1. Create a booking with RabbitMQ running normally — confirm the message is consumed almost immediately.
2. Stop RabbitMQ (`docker compose stop rabbitmq`).
3. Create another booking. The booking still succeeds; the outbox row stays `PENDING`.
4. Restart RabbitMQ (`docker compose start rabbitmq`).
5. Within one poll cycle (<=3s), the pending event is published and consumed — no confirmation was ever lost.