package com.hackathon.slot_booking_engine.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeadLetterConsumer {

    @RabbitListener(queues = "booking.events.dlq")
    public void consumeDeadLetterMessage(String failedMessage) {
        log.error("⚠️ [DEAD LETTER QUEUE] Poison pill message routed to DLQ for inspection: {}", failedMessage);
        // In production: Send alert to PagerDuty/Slack or persist to failed_jobs table
    }
}