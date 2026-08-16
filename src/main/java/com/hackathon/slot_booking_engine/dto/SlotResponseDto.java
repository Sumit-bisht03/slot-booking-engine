package com.hackathon.slot_booking_engine.dto;

import com.hackathon.slot_booking_engine.entity.enums.SlotStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotResponseDto {

    private Long slotId;
    private Long hostId;
    private String hostName;
    private Instant startTime;
    private Instant endTime;
    private SlotStatus status;
}
