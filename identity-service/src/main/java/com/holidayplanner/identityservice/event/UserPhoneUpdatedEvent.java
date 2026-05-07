package com.holidayplanner.identityservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Event payload: User has updated their phone number.
 * 
 * Published by: IdentityCommandService.updatePhoneNumber()
 * Topic: identity.user.phone_updated
 * Consumers: notification-service (if needed), audit logging
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPhoneUpdatedEvent {
    
    @JsonProperty("userId")
    private UUID userId;
    
    @JsonProperty("phoneNumber")
    private String phoneNumber;
    
    @JsonProperty("updatedAt")
    private Instant updatedAt;
}
