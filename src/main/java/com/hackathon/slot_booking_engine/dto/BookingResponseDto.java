package com.hackathon.slot_booking_engine.dto;

import com.hackathon.slot_booking_engine.entity.enums.BookingStatus;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponseDto {

    private Long bookingId;
    private Long slotId;
    private Long clientId;
    private String clientName;
    private String clientEmail;
    private Long hostId;
    private String hostName;
    private Instant startTime;
    private Instant endTime;
    private BookingStatus status;
    private String idempotencyKey;
    private Instant createdAt;

}
