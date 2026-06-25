package com.holidayplanner.identityservice.client;

import com.holidayplanner.identityservice.exception.ActiveBookingVetoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * HTTP client for calling booking-service from identity-service.
 * Used for composition queries to enrich user profiles with booking information.
 * 
 * Follows the same pattern as booking-service's EventServiceClient.
 * Uses fail-safe behavior for delete veto checks: if booking-service cannot be
 * reached or returns an error, deletion is rejected.
 */
@Slf4j
@Component
public class BookingServiceClient {

    private final RestClient restClient;
    private final String bookingServiceUrl;
    private final String serviceSecret;

    public BookingServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.booking-service.url:http://localhost:8082}") String bookingServiceUrl,
            @Value("${service.secret:holidayplanner-internal-service-secret}") String serviceSecret) {
        this.restClient = restClientBuilder.build();
        this.bookingServiceUrl = bookingServiceUrl;
        this.serviceSecret = serviceSecret;
    }

    /**
     * Get the count of active (CONFIRMED or WAITLISTED, non-CANCELLED) bookings for a family member.
     * 
     * @param familyMemberId the family member ID
     * @return count of active bookings
     * @throws ActiveBookingVetoException if booking-service cannot complete the veto check
     */
    public long getActiveBookingCount(UUID familyMemberId) {
        String url = bookingServiceUrl + "/api/bookings/family-member/" + familyMemberId + "/has-active";
        try {
            ActiveBookingCheckResponse response = restClient.get()
                    .uri(url)
                    .header("X-Service-Secret", serviceSecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ActiveBookingVetoException(
                                "Cannot verify active bookings for family member " + familyMemberId
                                        + "; deletion rejected");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ActiveBookingVetoException(
                                "Cannot verify active bookings for family member " + familyMemberId
                                        + "; deletion rejected");
                    })
                    .body(ActiveBookingCheckResponse.class);
            
            if (response == null) {
                throw new ActiveBookingVetoException(
                        "Cannot verify active bookings for family member " + familyMemberId
                                + "; deletion rejected");
            }
            return response.getActiveBookingCount();
        } catch (ActiveBookingVetoException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("Booking service unavailable when querying bookings for familyMemberId {}", familyMemberId, e);
            throw new ActiveBookingVetoException(
                    "Cannot verify active bookings for family member " + familyMemberId
                            + "; deletion rejected", e);
        } catch (RestClientException e) {
            log.warn("Booking service error for familyMemberId {}: {}", familyMemberId, e.getMessage());
            throw new ActiveBookingVetoException(
                    "Cannot verify active bookings for family member " + familyMemberId
                            + "; deletion rejected", e);
        }
    }

    public static class ActiveBookingCheckResponse {
        private boolean hasActiveBookings;
        private long activeBookingCount;

        public boolean isHasActiveBookings() {
            return hasActiveBookings;
        }

        public void setHasActiveBookings(boolean hasActiveBookings) {
            this.hasActiveBookings = hasActiveBookings;
        }

        public long getActiveBookingCount() {
            return activeBookingCount;
        }

        public void setActiveBookingCount(long activeBookingCount) {
            this.activeBookingCount = activeBookingCount;
        }
    }
}
