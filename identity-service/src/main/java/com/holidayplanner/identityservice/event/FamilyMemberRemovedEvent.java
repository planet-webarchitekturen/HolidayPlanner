package com.holidayplanner.identityservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Event payload: Family member (child) has been removed from a user's profile.
 * 
 * Published by: IdentityCommandService.removeFamilyMember()
 * Topic: identity.family_member.removed
 * Consumers: booking-service (cleanup orphaned bookings if needed)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMemberRemovedEvent {
    
    @JsonProperty("familyMemberId")
    private UUID familyMemberId;
    
    @JsonProperty("userId")
    private UUID userId;
    
    @JsonProperty("firstName")
    private String firstName;
    
    @JsonProperty("lastName")
    private String lastName;
    
    @JsonProperty("removedAt")
    private Instant removedAt;
}
