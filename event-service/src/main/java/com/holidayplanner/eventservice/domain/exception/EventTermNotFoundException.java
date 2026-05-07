package com.holidayplanner.eventservice.domain.exception;

import java.util.UUID;

public class EventTermNotFoundException extends RuntimeException {

    public EventTermNotFoundException(UUID eventTermId) {
        super("Event term not found: " + eventTermId);
    }
}
