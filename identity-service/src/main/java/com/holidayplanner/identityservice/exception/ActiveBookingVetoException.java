package com.holidayplanner.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ActiveBookingVetoException extends RuntimeException {

    public ActiveBookingVetoException(String message) {
        super(message);
    }

    public ActiveBookingVetoException(String message, Throwable cause) {
        super(message, cause);
    }
}
