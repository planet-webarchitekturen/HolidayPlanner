package com.holidayplanner.identityservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Base class for all domain events in the Identity Service.
 * 
 * Envelope structure includes:
 * - eventType: semantic event name for consumer routing
 * - version: schema version for forward/backward compatibility
 * - timestamp: server time for ordering/replay
 * - source: publishing service name for traceability
 * - payload: event-specific data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DomainEvent {
    
    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("version")
    private String version;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("source")
    private String source;
    
    @JsonProperty("payload")
    private Object payload;

    /**
     * Factory method to create domain events with standard metadata
     */
    public static DomainEvent of(String eventType, Object payload) {
        DomainEvent event = new DomainEvent();
        event.setEventType(eventType);
        event.setVersion("1");
        event.setTimestamp(Instant.now());
        event.setSource("identity-service");
        event.setPayload(payload);
        return event;
    }
}
