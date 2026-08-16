package com.hackathon.slot_booking_engine.exception;

public class SlotAlreadyBookedException extends RuntimeException{

    public SlotAlreadyBookedException(String message) {
        super(message);
    }
}
