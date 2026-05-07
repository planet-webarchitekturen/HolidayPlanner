package com.holidayplanner.eventservice.domain.exception;

import com.holidayplanner.shared.model.EventTermStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(EventTermStatus from, EventTermStatus to) {
        super("Invalid status transition: " + from + " -> " + to);
    }
}
