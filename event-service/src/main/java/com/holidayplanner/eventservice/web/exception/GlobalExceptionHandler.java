package com.holidayplanner.eventservice.web.exception;

import com.holidayplanner.eventservice.domain.exception.DownstreamServiceException;
import com.holidayplanner.eventservice.domain.exception.EventNotFoundException;
import com.holidayplanner.eventservice.domain.exception.EventTermNotActiveException;
import com.holidayplanner.eventservice.domain.exception.EventTermNotFoundException;
import com.holidayplanner.eventservice.domain.exception.InvalidCapacityException;
import com.holidayplanner.eventservice.domain.exception.InvalidStatusTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEventNotFound(EventNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EventTermNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEventTermNotFound(EventTermNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EventTermNotActiveException.class)
    public ResponseEntity<Map<String, Object>> handleNotActive(EventTermNotActiveException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidStatusTransitionException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidCapacityException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCapacity(InvalidCapacityException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<Map<String, Object>> handleDownstream(DownstreamServiceException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status.value()).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
