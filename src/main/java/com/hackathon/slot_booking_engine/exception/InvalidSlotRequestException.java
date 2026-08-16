package com.hackathon.slot_booking_engine.exception;

public class InvalidSlotRequestException extends RuntimeException {

    public InvalidSlotRequestException(String message) {
        super(message);
    }
}
