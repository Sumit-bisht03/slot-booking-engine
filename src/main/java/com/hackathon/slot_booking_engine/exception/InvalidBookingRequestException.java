package com.hackathon.slot_booking_engine.exception;

public class InvalidBookingRequestException extends RuntimeException{

    public InvalidBookingRequestException(String message) {
        super(message);
    }
}
