package com.holidayplanner.identityservice.dto;

import com.holidayplanner.shared.model.User;
import com.holidayplanner.shared.model.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * DTO for an enriched user profile.
 * Composition query: combines user info with family members and their booking counts.
 * 
 * This is a composition response that bundles data from multiple services:
 * - User info from identity-service DB
 * - Family members from identity-service DB
 * - Booking counts from booking-service (via BookingServiceClient)
 * 
 * Benefit: frontend makes one API call instead of multiple calls to different services.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserProfileEnrichedResponse {
    private UUID id;
    private String email;
    private String phoneNumber;
    private UUID organizationId;
    private UserRole role;
    private List<FamilyMemberWithBookingsResponse> familyMembers;

    public static UserProfileEnrichedResponse from(User user, List<FamilyMemberWithBookingsResponse> enrichedFamilyMembers) {
        UserProfileEnrichedResponse r = new UserProfileEnrichedResponse();
        r.id = user.getId();
        r.email = user.getEmail();
        r.phoneNumber = user.getPhoneNumber();
        r.organizationId = user.getOrganizationId();
        r.role = user.getRole();
        r.familyMembers = enrichedFamilyMembers;
        return r;
    }
}
