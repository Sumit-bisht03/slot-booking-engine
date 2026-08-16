package com.hackathon.slot_booking_engine.controller;

import com.hackathon.slot_booking_engine.dto.ApiResponse;
import com.hackathon.slot_booking_engine.dto.BookingRequestDto;
import com.hackathon.slot_booking_engine.dto.BookingResponseDto;
import com.hackathon.slot_booking_engine.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking Operations")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Create a slot booking", description = "Reserves a slot using Redisson distributed lock and optimistic DB locking.")
    public ResponseEntity<ApiResponse<BookingResponseDto>> createBooking(
            @Parameter(description = "User ID header", example = "100")
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,

            @Parameter(description = "Idempotency UUID header", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyHeader,

            @Valid @RequestBody BookingRequestDto requestDto) {

        BookingResponseDto response = bookingService.createBooking(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("created",response));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get booking details", description = "Fetches booking metadata with Redis cache support.")
    public ResponseEntity<ApiResponse<BookingResponseDto>> getBooking(
            @Parameter(description = "Booking ID", example = "1")
            @PathVariable Long bookingId) {

        BookingResponseDto response = bookingService.getBookingById(bookingId);
        return ResponseEntity.ok(ApiResponse.success("created",response));
    }

    @PutMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel booking", description = "Cancels booking and evicts Redis cache.")
    public ResponseEntity<ApiResponse<BookingResponseDto>> cancelBooking(
            @Parameter(description = "Booking ID to cancel", example = "1")
            @PathVariable Long bookingId,

            @Parameter(description = "User requesting cancellation", example = "100")
            @RequestHeader("X-User-Id") Long userId) {

        BookingResponseDto response = bookingService.cancelBooking(bookingId, userId);
        return ResponseEntity.ok(ApiResponse.success("Cancelled",response));
    }
}