package com.holidayplanner.identityservice.composition;

import com.holidayplanner.shared.model.User;
import com.holidayplanner.identityservice.client.BookingServiceClient;
import com.holidayplanner.identityservice.dto.FamilyMemberWithBookingsResponse;
import com.holidayplanner.identityservice.dto.UserProfileEnrichedResponse;
import com.holidayplanner.identityservice.query.IdentityQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Composition Service for Identity Service.
 * 
 * Composition queries combine data from multiple sources (services) into a single enriched response.
 * This pattern reduces the number of API calls clients must make and simplifies frontend logic.
 * 
 * Separated from IdentityQueryService because composition queries:
 * - Call external services (e.g., booking-service)
 * - Have their own failure handling and resilience concerns
 * - Are expensive (compared to simple read queries)
 * - May benefit from caching in the future
 * 
 * Following the pattern from booking-service, which implements:
 * - getBookingsForFamilyMemberEnriched() — bookings + event details
 * - getEventTermSummary() — booking counts + event capacity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityCompositionService {

    private final IdentityQueryService queryService;
    private final BookingServiceClient bookingServiceClient;

    /**
     * Get a user's complete profile enriched with family member booking information.
     * 
     * This composition query combines:
     * - User profile from identity-service
     * - Family members from identity-service  
     * - Active booking counts from booking-service (per family member)
     * 
     * Benefit: Frontend sees the complete family picture in one call.
     * Without composition, frontend would need:
     * 1. GET /users/{userId}
     * 2. GET /users/{userId}/family-members
     * 3. GET /bookings/family-member/{memberId} (for each family member)
     * 
     * That's 2 + N API calls. With composition, it's just 1 call.
     * 
     * Failure handling (graceful degradation):
     * - If booking-service is unavailable, family members are still returned
     * - Their activeBookingCount will be 0
     * - A WARN log entry records which booking-service call failed
     * 
     * @param userId the user's ID
     * @return UserProfileEnrichedResponse with family members and booking counts
     * @throws RuntimeException if user not found
     */
    public UserProfileEnrichedResponse getUserProfileEnriched(UUID userId) {
        // Fetch user and their family members
        User user = queryService.getUserById(userId);
        var familyMembers = queryService.getFamilyMembers(userId);
        
        // Enrich each family member with booking count from booking-service
        List<FamilyMemberWithBookingsResponse> enriched = familyMembers.stream()
                .map(member -> {
                    try {
                        long bookingCount = bookingServiceClient.getActiveBookingCount(member.getId());
                        return FamilyMemberWithBookingsResponse.from(member, bookingCount);
                    } catch (Exception e) {
                        log.warn("Could not fetch booking count for family member {}", member.getId(), e);
                        // Return with booking count = 0 (graceful degradation)
                        return FamilyMemberWithBookingsResponse.from(member, 0L);
                    }
                })
                .collect(Collectors.toList());
        
        return UserProfileEnrichedResponse.from(user, enriched);
    }
}
