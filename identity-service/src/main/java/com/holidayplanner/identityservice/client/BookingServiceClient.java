package com.holidayplanner.identityservice.client;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
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
 * Implements graceful degradation: if booking-service is unavailable, 
 * returns 0 rather than failing the entire request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
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
     * @return count of active bookings, or 0 if booking-service is unavailable
     */
    public long getActiveBookingCount(UUID familyMemberId) {
        String url = bookingServiceUrl + "/api/bookings/family-member/" + familyMemberId;
        try {
            var response = restClient.get()
                    .uri(url)
                    .header("X-Service-Secret", serviceSecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("Booking service returned 4xx for familyMemberId {}: {}", familyMemberId, res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.warn("Booking service returned 5xx for familyMemberId {}: {}", familyMemberId, res.getStatusCode());
                    })
                    .body(Object[].class);
            
            return response != null ? response.length : 0L;
        } catch (ResourceAccessException e) {
            log.warn("Booking service unavailable when querying bookings for familyMemberId {}", familyMemberId, e);
            return 0L;
        } catch (RestClientException e) {
            log.warn("Booking service error for familyMemberId {}: {}", familyMemberId, e.getMessage());
            return 0L;
        }
    }
}
