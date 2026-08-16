package com.hackathon.slot_booking_engine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("High-Concurrency Slot Booking Engine API")
                        .version("1.0.0")
                        .description("Production-grade slot booking system with 5-layer concurrency defense, transactional outbox, and RabbitMQ quorum queues.")
                        .contact(new Contact().name("Slotify Team")));
    }
}