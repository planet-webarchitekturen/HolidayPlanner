package com.holidayplanner.bookingservice.exception;

public class OrganizationServiceException extends RuntimeException {
    public OrganizationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
