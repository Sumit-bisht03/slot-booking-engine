package com.hackathon.slot_booking_engine.concurrency;

import com.hackathon.slot_booking_engine.dto.BookingRequestDto;
import com.hackathon.slot_booking_engine.entity.Slot;
import com.hackathon.slot_booking_engine.entity.User;
import com.hackathon.slot_booking_engine.entity.enums.SlotStatus;
import com.hackathon.slot_booking_engine.entity.enums.UserRole;
import com.hackathon.slot_booking_engine.repository.BookingRepository;
import com.hackathon.slot_booking_engine.repository.SlotRepository;
import com.hackathon.slot_booking_engine.repository.UserRepository;
import com.hackathon.slot_booking_engine.service.BookingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the double-booking guarantee under real concurrent load.
 *
 * IMPORTANT: this is a real integration test, not a unit test - it needs
 * an actual Postgres + Redis reachable at the addresses in application.yml
 * (i.e. `docker compose up -d` running locally), since the whole point is
 * to exercise the real Redisson lock and the real DB's optimistic version
 * check, not a mock of either.
 *
 * Deliberately NOT @Transactional at the class level: wrapping this test in
 * a single transaction would mean every thread shares one DB connection's
 * transaction context, which defeats the purpose of proving the lock/version
 * check works across genuinely independent concurrent transactions - and is
 * also not how real concurrent requests behave in production.
 */
@SpringBootTest
class ConcurrentBookingTest {

    private static final int CONCURRENT_REQUESTS = 50;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private BookingRepository bookingRepository;

    private User testHost;
    private User testClient;
    private Slot testSlot;

    @BeforeEach
    void setUp() {
        // Fresh fixtures per run, unique by UUID, so repeated test runs
        // never collide with leftover data from a previous run.
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        testHost = userRepository.save(User.builder()
                .name("Test Host " + uniqueSuffix)
                .email("host+" + uniqueSuffix + "@test.slotify.com")
                .role(UserRole.HOST)
                .build());

        testClient = userRepository.save(User.builder()
                .name("Test Client " + uniqueSuffix)
                .email("client+" + uniqueSuffix + "@test.slotify.com")
                .role(UserRole.CLIENT)
                .build());

        Instant startTime = Instant.now().plus(3, ChronoUnit.DAYS);
        testSlot = slotRepository.save(Slot.builder()
                .host(testHost)
                .startTime(startTime)
                .endTime(startTime.plus(1, ChronoUnit.HOURS))
                .status(SlotStatus.AVAILABLE)
                .build());
    }

    @AfterEach
    void tearDown() {
        // Best-effort cleanup so repeated local runs don't accumulate junk
        // rows. Not wrapped in a transaction, so failures here don't hide
        // a genuine test failure above.
        bookingRepository.findBySlotId(testSlot.getId()).ifPresent(bookingRepository::delete);
        slotRepository.deleteById(testSlot.getId());
        userRepository.deleteById(testClient.getId());
        userRepository.deleteById(testHost.getId());
    }

    @Test
    void onlyOneBookingSucceedsWhenManyThreadsRaceForTheSameSlot() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch startingGun = new CountDownLatch(1);          // holds all threads at the gate
        CountDownLatch allFinished = new CountDownLatch(CONCURRENT_REQUESTS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<String> unexpectedErrors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                try {
                    startingGun.await(); // block here until every thread is ready, so they all fire together

                    BookingRequestDto request = BookingRequestDto.builder()
                            .slotId(testSlot.getId())
                            .clientId(testClient.getId())
                            .idempotencyKey(UUID.randomUUID().toString()) // unique per attempt - this test targets the LOCK, not idempotency
                            .build();

                    bookingService.createBooking(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // Both the Redisson-lock-contention path and the
                    // slot-already-booked path are EXPECTED outcomes for
                    // every thread except the winner - only count and
                    // report anything that isn't one of those two.
                    String exceptionName = e.getClass().getSimpleName();
                    if (exceptionName.equals("SlotAlreadyBookedException")
                            || exceptionName.equals("ConcurrentBookingException")) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(exceptionName + ": " + e.getMessage());
                    }
                } finally {
                    allFinished.countDown();
                }
            });
        }

        startingGun.countDown(); // release every thread at once
        boolean completedInTime = allFinished.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completedInTime, "All " + CONCURRENT_REQUESTS + " requests should finish within 30s");
        assertTrue(unexpectedErrors.isEmpty(),
                "No unexpected exceptions should occur - got: " + unexpectedErrors);

        System.out.println("Concurrent booking test: " + successCount.get() + " succeeded, "
                + conflictCount.get() + " correctly rejected as conflicts, out of "
                + CONCURRENT_REQUESTS + " total requests.");

        assertEquals(1, successCount.get(),
                "Exactly one booking should succeed when " + CONCURRENT_REQUESTS + " threads race for the same slot");
        assertEquals(CONCURRENT_REQUESTS - 1, conflictCount.get(),
                "Every other attempt should be rejected as a conflict, not silently dropped or errored");

        // Also verify the DB's own view of the world agrees with the counters above.
        boolean bookingExistsForSlot = bookingRepository.findBySlotId(testSlot.getId()).isPresent();
        assertTrue(bookingExistsForSlot, "A Booking row should exist for the slot");
    }
}