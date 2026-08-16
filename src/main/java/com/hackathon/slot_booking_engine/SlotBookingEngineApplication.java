package com.hackathon.slot_booking_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SlotBookingEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(SlotBookingEngineApplication.class, args);
	}

}
