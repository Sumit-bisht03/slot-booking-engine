package com.hackathon.slot_booking_engine.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange:booking.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.queue:booking.events.queue}")
    private String queueName;

    @Value("${rabbitmq.routing-key:booking.event.key}")
    private String routingKey;

    // --- Primary Messaging Topology ---

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue bookingQueue() {
        Map<String, Object> args = new HashMap<>();
        // Configure main queue to route failed messages to DLX
        args.put("x-dead-letter-exchange", "booking.dlx");
        args.put("x-dead-letter-routing-key", "booking.dlq.key");
        args.put("x-delivery-limit", 3); // Native broker-level DLQ routing after 3 tries

        return QueueBuilder.durable(queueName)
                .quorum() // Configures x-queue-type: quorum
                .withArguments(args)
                .build();
    }

    @Bean
    public Binding bookingBinding(Queue bookingQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingQueue).to(bookingExchange).with(routingKey);
    }

    // --- Dead Letter Queue (DLQ) Topology ---

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("booking.dlx", true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("booking.events.dlq").build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("booking.dlq.key");
    }
}