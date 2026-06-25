package com.holidayplanner.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(ActiveBookingVetoException.class)
    public ResponseEntity<Map<String, Object>> handleActiveBookingVeto(ActiveBookingVetoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "error", HttpStatus.CONFLICT.getReasonPhrase(),
                "message", e.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
