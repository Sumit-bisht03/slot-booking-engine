package com.hackathon.slot_booking_engine.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hackathon.slot_booking_engine.entity.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;


@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(
        name = "bookings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_slot", columnNames = "slot_id")
        },
        indexes = {
                @Index(name = "idx_booking_client", columnList = "client_id"),
                @Index(name = "idx_booking_idempotency", columnList = "idempotencyKey")
        }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnore
    private User client;

    @OneToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    @JsonIgnore
    private Slot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(unique = true)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate(){
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if(this.status == null){
            this.status = BookingStatus.CONFIRMED;
        }
    }

    @PreUpdate
    private void onUpdate(){
        this.updatedAt = Instant.now();
    }
}
