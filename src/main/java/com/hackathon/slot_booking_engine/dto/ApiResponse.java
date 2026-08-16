package com.hackathon.slot_booking_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private String message;
    private boolean success;
    private T data;

    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(String message, T data) {
        return  ApiResponse.<T>builder()
                .message(message)
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return  ApiResponse.<T>builder()
                .message(message)
                .success(false)
                .data(null)
                .build();
    }
}
