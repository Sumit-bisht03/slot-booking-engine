package com.hackathon.slot_booking_engine.exception;

public class ConcurrentBookingException extends RuntimeException{

    public ConcurrentBookingException(String message) {
        super(message);
    }
}
