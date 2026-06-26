package com.holidayplanner.identityservice.client;

import com.holidayplanner.identityservice.exception.BookingServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * HTTP client for calling booking-service from identity-service.
 * Used by the family-member / user deletion veto to check for active bookings.
 *
 * <p>Fails <b>safe</b>: if booking-service cannot be reached we cannot prove the member has no
 * active bookings, so we throw and the caller rejects the deletion (rather than silently allowing it).
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
     * @return true if the family member has at least one active (CONFIRMED or WAITLISTED) booking.
     * @throws BookingServiceUnavailableException if booking-service cannot be reached (fail-safe reject).
     */
    public boolean hasActiveBookings(UUID familyMemberId) {
        String url = bookingServiceUrl + "/api/bookings/family-member/" + familyMemberId + "/has-active";
        try {
            Boolean result = restClient.get()
                    .uri(url)
                    .header("X-Service-Secret", serviceSecret)
                    .retrieve()
                    .body(Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (RestClientException e) {
            log.warn("Could not verify active bookings for family member {} — failing safe (reject delete): {}",
                    familyMemberId, e.getMessage());
            throw new BookingServiceUnavailableException(familyMemberId, e);
        }
    }
}
