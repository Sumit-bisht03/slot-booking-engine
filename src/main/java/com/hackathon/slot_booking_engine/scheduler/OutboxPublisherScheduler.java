package com.hackathon.slot_booking_engine.scheduler;

import com.hackathon.slot_booking_engine.entity.OutboxMessage;
import com.hackathon.slot_booking_engine.entity.enums.OutboxStatus;
import com.hackathon.slot_booking_engine.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisherScheduler {
    private static final int MAX_RETRIES = 3;

    private final RabbitTemplate rabbitTemplate;
    private final OutboxMessageRepository outboxMessageRepository;

    @Value("${rabbitmq.exchange:booking.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key:booking.event.key}")
    private String routingKey;

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:3000}")
    @Transactional
    public void processPendingOutboxMessages(){
        List<OutboxMessage> pendingMessages = outboxMessageRepository.findPendingMessages(
                OutboxStatus.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0,20));
        if (pendingMessages.isEmpty()){
            return;
        }

        log.info("Found {} PENDING outbox events to publish", pendingMessages.size());
        for (OutboxMessage message: pendingMessages){
            try{
                rabbitTemplate.convertAndSend(exchange,routingKey, message.getPayload());
                message.setStatus(OutboxStatus.PROCESSED);
                outboxMessageRepository.save(message);
            }catch (Exception e){
                int currentRetryCount = message.getRetryCount() + 1;
                message.setRetryCount(currentRetryCount);

                if (currentRetryCount >= MAX_RETRIES) {
                    // Poison message/persistent failure -> Mark as FAILED
                    message.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event ID={} reached MAX_RETRIES ({}). Marked as FAILED. Error: {}",
                            message.getId(), MAX_RETRIES, e.getMessage());
                } else {
                    // Exponential backoff formula: 2^retryCount * 2 seconds (2s, 4s, 8s...)
                    long backoffSeconds = (long) Math.pow(2, currentRetryCount) * 2;
                    message.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                }
                outboxMessageRepository.save(message);
            }
        }
    }
}
