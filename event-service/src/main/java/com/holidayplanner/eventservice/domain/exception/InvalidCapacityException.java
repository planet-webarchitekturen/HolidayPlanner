package com.holidayplanner.eventservice.domain.exception;

public class InvalidCapacityException extends RuntimeException {

    public InvalidCapacityException(String message) {
        super(message);
    }
}
