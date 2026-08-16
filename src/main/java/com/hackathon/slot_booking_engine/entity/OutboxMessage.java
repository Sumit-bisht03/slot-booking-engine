package com.hackathon.slot_booking_engine.entity;

import com.hackathon.slot_booking_engine.entity.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(
        name = "outbox_messages",
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, createdAt")
        }
)
public class OutboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String aggregateType; // e.g., "BOOKING"

    @Column(nullable = false)
    private String aggregateId;   // e.g., Booking ID

    @Column(nullable = false)
    private String eventType;       // e.g., "BOOKING_CREATED"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;         // Serialized JSON of event data

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant processedAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) {
            this.status = OutboxStatus.PENDING;
        }
        this.retryCount = 0;
    }
}
