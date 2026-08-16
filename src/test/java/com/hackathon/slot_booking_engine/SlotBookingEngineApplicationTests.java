package com.hackathon.slot_booking_engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SlotBookingEngineApplicationTests {

	@LocalServerPort
	private int port;

	@Test
	void openApiDocsEndpointLoads() {
		RestClient restClient = RestClient.create("http://localhost:" + port);

		ResponseEntity<String> response = restClient.get()
				.uri("/v3/api-docs")
				.retrieve()
				.toEntity(String.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"openapi\""));
	}

}
