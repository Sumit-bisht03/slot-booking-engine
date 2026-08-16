package com.hackathon.slot_booking_engine.controller;

import com.hackathon.slot_booking_engine.dto.ApiResponse;
import com.hackathon.slot_booking_engine.dto.SlotCreateRequestDto;
import com.hackathon.slot_booking_engine.dto.SlotResponseDto;
import com.hackathon.slot_booking_engine.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/slots")
public class SlotController {

    private final SlotService slotService;

    @PostMapping
    @Operation(summary = "Create a slot", description = "Host posts a new availability window. Rejects overlaps with the host's existing slots.")
    public ResponseEntity<ApiResponse<SlotResponseDto>> createSlot(@Valid @RequestBody SlotCreateRequestDto request) {
        SlotResponseDto response = slotService.createSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Slot created successfully", response));
    }

    @GetMapping("/available")
    ResponseEntity<ApiResponse<List<SlotResponseDto>>> getAllAvailableSlots(){
        List<SlotResponseDto> slots = slotService.getAllAvailableSlots();
        return ResponseEntity.ok(ApiResponse.success("Available slots retrieved successfully",slots));
    }

    @GetMapping("/host/{hostId}")
    ResponseEntity<ApiResponse<List<SlotResponseDto>>> getAvailableSlotsForHost(@PathVariable Long hostId){
        List<SlotResponseDto> slots = slotService.getAvailableSlotsForHost(hostId);
        return ResponseEntity.ok(ApiResponse.success("Host slots retrieved successfully",slots));
    }
}
