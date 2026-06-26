package com.holidayplanner.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FamilyMemberHasActiveBookingsException.class)
    public ResponseEntity<Map<String, Object>> handleActiveBookings(FamilyMemberHasActiveBookingsException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BookingServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleBookingServiceUnavailable(BookingServiceUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status.value()).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "timestamp", LocalDateTime.now().toString()));
    }
}
