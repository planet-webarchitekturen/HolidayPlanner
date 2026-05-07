package com.holidayplanner.identityservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Event payload: User has registered in the system.
 * 
 * Published by: IdentityCommandService.registerUser()
 * Topic: identity.user.registered
 * Consumers: notification-service (send welcome email), organization-service (track members)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {
    
    @JsonProperty("userId")
    private UUID userId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("organizationId")
    private UUID organizationId;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
}
