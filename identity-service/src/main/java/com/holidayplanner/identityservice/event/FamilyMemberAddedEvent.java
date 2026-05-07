package com.holidayplanner.identityservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event payload: Family member (child) has been added to a user's profile.
 * 
 * Published by: IdentityCommandService.addFamilyMember()
 * Topic: identity.family_member.added
 * Consumers: booking-service (notify about new eligible participant)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMemberAddedEvent {
    
    @JsonProperty("familyMemberId")
    private UUID familyMemberId;
    
    @JsonProperty("userId")
    private UUID userId;
    
    @JsonProperty("firstName")
    private String firstName;
    
    @JsonProperty("lastName")
    private String lastName;
    
    @JsonProperty("birthDate")
    private LocalDate birthDate;
    
    @JsonProperty("zip")
    private String zip;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
}
