package com.holidayplanner.identityservice.exception;

import java.util.UUID;

/**
 * Thrown when booking-service cannot be reached to verify a deletion veto.
 * The deletion is rejected (fail-safe) because we cannot prove there are no active bookings.
 */
public class BookingServiceUnavailableException extends RuntimeException {
    public BookingServiceUnavailableException(UUID memberId, Throwable cause) {
        super("Cannot verify active bookings for family member " + memberId
                + " (booking-service unavailable); refusing deletion.", cause);
    }
}
