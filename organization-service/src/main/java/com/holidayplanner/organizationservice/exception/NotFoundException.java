package com.holidayplanner.organizationservice.exception;

/**
 * Thrown when a requested organization, team member or sponsor does not exist. Mapped to HTTP 404 by
 * {@link com.holidayplanner.organizationservice.controller.GlobalExceptionHandler}. Replaces the
 * previous pattern of throwing a bare {@code RuntimeException} whose message had to contain the text
 * "not found" to be mapped correctly.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
