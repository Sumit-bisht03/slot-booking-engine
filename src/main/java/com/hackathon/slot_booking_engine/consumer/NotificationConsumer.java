package com.hackathon.slot_booking_engine.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationConsumer {

    @RabbitListener(queues = "${rabbitmq.queue:booking.events.queue}")
    public void consumeBookingEvent(
            String eventJsonPayload,
            @Header(name = "x-delivery-count", defaultValue = "1") Long deliveryCount) {

        log.info("📢 [NOTIFICATION SERVICE] Processing event (Attempt {}/3)...", deliveryCount);

        // 1. Simulate failure if payload contains trigger flag
        if (simulateFailure(eventJsonPayload)) {
            log.warn("⚠️ [NOTIFICATION SERVICE] Transient error on attempt {}. Throwing exception...", deliveryCount);

            // Throwing a normal exception triggers RabbitMQ redelivery (up to x-delivery-limit: 3)
            // Once deliveryCount hits 3, RabbitMQ automatically routes it to booking.events.dlq
            throw new RuntimeException("Simulated transient failure for notification processing");
        }

        // 2. Business Logic Execution
        log.info("✅ [NOTIFICATION SERVICE] Successfully sent confirmation email for payload: {}", eventJsonPayload);
    }

    public Boolean simulateFailure(String payload) {
        return payload != null && payload.contains("SIMULATE_FAILURE");
    }
}