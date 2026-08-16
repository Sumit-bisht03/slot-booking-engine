package com.hackathon.slot_booking_engine.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequestDto {

    @NotNull(message = "Slot ID is required")
    private Long slotId;

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @NotNull(message = "Idempotency Key is required")
    private String idempotencyKey;
}
