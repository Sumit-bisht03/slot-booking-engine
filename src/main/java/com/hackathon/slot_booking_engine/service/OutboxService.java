package com.hackathon.slot_booking_engine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.slot_booking_engine.dto.BookingRequestDto;
import com.hackathon.slot_booking_engine.dto.BookingResponseDto;
import com.hackathon.slot_booking_engine.entity.Booking;
import com.hackathon.slot_booking_engine.entity.OutboxMessage;
import com.hackathon.slot_booking_engine.entity.enums.OutboxStatus;
import com.hackathon.slot_booking_engine.repository.BookingRepository;
import com.hackathon.slot_booking_engine.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveEvent(String aggregateType, String aggregateId , String eventType, Object payload){
        try{
            String jsonPayload = objectMapper.writeValueAsString(payload);
            OutboxMessage msg = OutboxMessage.builder()
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .eventType(eventType)
                    .payload(jsonPayload)
                    .status(OutboxStatus.PENDING)
                    .build();
            outboxMessageRepository.save(msg);
            log.info("Outbox event saved: type={}, aggregateId={}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for aggregateId: {}", aggregateId, e);
            throw new RuntimeException("Error processing outbox event payload", e);
        }
    }

}
