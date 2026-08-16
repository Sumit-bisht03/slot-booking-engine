package com.hackathon.slot_booking_engine.service;

import com.hackathon.slot_booking_engine.dto.BookingRequestDto;
import com.hackathon.slot_booking_engine.dto.BookingResponseDto;
import com.hackathon.slot_booking_engine.entity.Booking;
import com.hackathon.slot_booking_engine.entity.OutboxMessage;
import com.hackathon.slot_booking_engine.entity.Slot;
import com.hackathon.slot_booking_engine.entity.enums.BookingStatus;
import com.hackathon.slot_booking_engine.entity.enums.OutboxStatus;
import com.hackathon.slot_booking_engine.entity.enums.SlotStatus;
import com.hackathon.slot_booking_engine.exception.ResourceNotFoundException;
import com.hackathon.slot_booking_engine.repository.BookingRepository;
import com.hackathon.slot_booking_engine.repository.OutboxMessageRepository;
import com.hackathon.slot_booking_engine.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {
    private final BookingTransactionService bookingTransactionService;
    private final SlotRepository slotRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final BookingRepository bookingRepository;
    private final DistributedLockService distributedLockService;

    @CacheEvict(value = {"availableSlots", "slotDetails"}, allEntries = true)
    public BookingResponseDto createBooking(BookingRequestDto request) {
        String lockKey = "lock:slot:" + request.getSlotId();
        // Layer 1 Defense: Acquire Redis Distributed Lock (wait up to 3s, lease for 5s)
        long waitTimeSeconds = 3;
        long leaseTimeSeconds = 5;
        return distributedLockService.executeWithLock(lockKey, waitTimeSeconds, leaseTimeSeconds, () ->
                bookingTransactionService.processBookingTransaction(request, this)
        );
    }

    @Transactional
    @CacheEvict(value = {"availableSlots", "slotDetails"}, allEntries = true)
    public BookingResponseDto cancelBooking(Long bookingId, Long userId) {
        // 1. Fetch booking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId));

        // 2. Validate ownership/permissions
        if (booking.getClient() == null || !booking.getClient().getId().equals(userId)) {
            throw new IllegalStateException("User unauthorized to cancel this booking");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking is already cancelled");
        }

        // 3. Mark booking CANCELLED
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // 4. Reset slot status to AVAILABLE
        Slot slot = booking.getSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        slotRepository.save(slot);

        // 5. Record transactional outbox event for cancellation
        OutboxMessage outboxEvent = OutboxMessage.builder()
                .aggregateType("BOOKING")
                .aggregateId(String.valueOf(booking.getId()))
                .eventType("BOOKING_CANCELLED")
                .payload("{\"bookingId\":" + bookingId + ",\"slotId\":" + slot.getId() + "}")
                .status(OutboxStatus.PENDING)
                .build();
        outboxMessageRepository.save(outboxEvent);

        return mapToDto(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponseDto getBookingById(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() ->
                new ResourceNotFoundException("Booking not found with ID: " + bookingId));
        return mapToDto(booking);
    }

    public BookingResponseDto mapToDto(Booking booking) {
        return BookingResponseDto.builder()
                .bookingId(booking.getId())
                .slotId(booking.getSlot().getId())
                .idempotencyKey(booking.getIdempotencyKey())
                .clientEmail(booking.getClient().getEmail())
                .clientId(booking.getClient().getId())
                .clientName(booking.getClient().getName())
                .hostName(booking.getSlot().getHost().getName())
                .hostId(booking.getSlot().getHost().getId())
                .startTime(booking.getSlot().getStartTime())
                .endTime(booking.getSlot().getEndTime())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}