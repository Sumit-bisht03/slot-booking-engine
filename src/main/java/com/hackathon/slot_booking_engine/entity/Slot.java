package com.hackathon.slot_booking_engine.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hackathon.slot_booking_engine.entity.enums.SlotStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;


@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "slots",
        indexes = {
                @Index(name = "idx_slot_host_start", columnList = "host_id, startTime"),
                @Index(name = "idx_slot_status", columnList = "status")
        })
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    @JsonIgnore
    private User host;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SlotStatus status;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate(){
        this.createdAt = Instant.now();
        if(this.status == null){
            this.status = SlotStatus.AVAILABLE;
        }
    }
}
