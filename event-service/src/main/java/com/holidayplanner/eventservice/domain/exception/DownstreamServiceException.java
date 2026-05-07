package com.holidayplanner.eventservice.domain.exception;

/**
 * Thrown when a downstream microservice call fails (network, 5xx, timeout).
 */
public class DownstreamServiceException extends RuntimeException {

    public DownstreamServiceException(String serviceName, String message, Throwable cause) {
        super(serviceName + ": " + message, cause);
    }
}
