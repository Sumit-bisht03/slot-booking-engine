package com.hackathon.slot_booking_engine.repository;

import com.hackathon.slot_booking_engine.entity.OutboxMessage;
import com.hackathon.slot_booking_engine.entity.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
    // Used by background poller to fetch pending events batch-by-batch.
    // Only picks up rows that are due for (re)delivery, honoring the exponential
    // backoff written to next_retry_at by the scheduler - otherwise a poll every
    // fixedDelay would retry immediately regardless of backoff and burn through
    // MAX_RETRIES before a transient outage (e.g. broker restart) even clears.
    @Query("SELECT o FROM OutboxMessage o WHERE o.status = :status " +
            "AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now) " +
            "ORDER BY o.createdAt ASC")
    List<OutboxMessage> findPendingMessages(@Param("status") OutboxStatus status,
                                             @Param("now") LocalDateTime now,
                                             Pageable pageable);
}
