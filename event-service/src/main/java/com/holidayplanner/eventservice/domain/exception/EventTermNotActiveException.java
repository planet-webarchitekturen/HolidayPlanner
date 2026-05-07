package com.holidayplanner.eventservice.domain.exception;

import java.util.UUID;

public class EventTermNotActiveException extends RuntimeException {

    public EventTermNotActiveException(UUID eventTermId) {
        super("Event term is not ACTIVE: " + eventTermId);
    }
}
