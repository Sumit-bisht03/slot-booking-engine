package com.hackathon.slot_booking_engine.service;

import com.hackathon.slot_booking_engine.dto.BookingRequestDto;
import com.hackathon.slot_booking_engine.dto.BookingResponseDto;
import com.hackathon.slot_booking_engine.entity.Booking;
import com.hackathon.slot_booking_engine.entity.Slot;
import com.hackathon.slot_booking_engine.entity.User;
import com.hackathon.slot_booking_engine.entity.enums.BookingStatus;
import com.hackathon.slot_booking_engine.entity.enums.SlotStatus;
import com.hackathon.slot_booking_engine.entity.enums.UserRole;
import com.hackathon.slot_booking_engine.exception.InvalidBookingRequestException;
import com.hackathon.slot_booking_engine.exception.ResourceNotFoundException;
import com.hackathon.slot_booking_engine.exception.SlotAlreadyBookedException;
import com.hackathon.slot_booking_engine.repository.BookingRepository;
import com.hackathon.slot_booking_engine.repository.SlotRepository;
import com.hackathon.slot_booking_engine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingTransactionService {
    private final UserRepository userRepository;
    private final SlotRepository slotRepository;
    private final OutboxService outboxService;
    private final BookingRepository bookingRepository;


    @Transactional
    public BookingResponseDto processBookingTransaction(BookingRequestDto request, BookingService bookingService){
        // Layer 2 Defense: Check Idempotency Key
        Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if(existingBooking.isPresent()){
            log.info("Duplicate request detected with idempotency key: {}", request.getIdempotencyKey());
            return bookingService.mapToDto(existingBooking.get());
        }
        User client = userRepository.findById(request.getClientId()).orElseThrow(() ->
                new ResourceNotFoundException("Client not found with id : "+request.getClientId()));
        if (client.getRole() != UserRole.CLIENT){
            throw new InvalidBookingRequestException("User with ID " + request.getClientId() + " is not a valid Client");
        }

        // Fetch Slot with host pre-fetched (prevents LazyInitializationException)
        Slot slot = slotRepository.findByIdWithHost(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with ID: " + request.getSlotId()));

        // Layer 3 Defense (App State): Check if slot is available
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new SlotAlreadyBookedException("Slot ID " + request.getSlotId() + " is already booked");
        }

        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);// Trigger @Version optimistic check
        Booking booking = Booking.builder()
                .slot(slot)
                .client(client)
                .idempotencyKey(request.getIdempotencyKey())
                .status(BookingStatus.CONFIRMED)
                .build();
        Booking savedBooking = bookingRepository.save(booking);

        // Transactional Outbox Pattern: Save event payload in same SQL transaction
        BookingResponseDto responseDto = bookingService.mapToDto(savedBooking);
        outboxService.saveEvent("BOOKING", savedBooking.getId().toString(),"BOOKING_CONFIRMED",responseDto);
        log.info("Booking confirmed successfully: bookingId={}, slotId={}", savedBooking.getId(), slot.getId());
        return responseDto;
    }

}
