package com.holidayplanner.eventservice.client;

import com.holidayplanner.eventservice.domain.exception.DownstreamServiceException;
import com.holidayplanner.eventservice.port.BookingServicePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class BookingServiceClient implements BookingServicePort {

    private static final ParameterizedTypeReference<List<String>> LIST_STRING =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public BookingServiceClient(
            @Value("${services.booking-service.url:http://localhost:8082}") String bookingServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(bookingServiceUrl).build();
    }

    @Override
    public long getConfirmedBookingCount(UUID eventTermId) {
        try {
            Long count = restClient.get()
                    .uri("/api/bookings/event-term/{id}/count", eventTermId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new DownstreamServiceException("booking-service",
                                "Booking count returned client error: " + res.getStatusCode(), null);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new DownstreamServiceException("booking-service",
                                "Booking count returned server error: " + res.getStatusCode(), null);
                    })
                    .body(Long.class);
            return count != null ? count : 0L;
        } catch (DownstreamServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new DownstreamServiceException("booking-service", "Unavailable", e);
        } catch (RestClientException e) {
            throw new DownstreamServiceException("booking-service", e.getMessage(), e);
        }
    }

    @Override
    public List<String> getParticipantParentEmails(UUID eventTermId) {
        return getOptionalList("/api/bookings/event-term/{id}/emails", eventTermId, "participant emails");
    }

    @Override
    public List<String> getParticipantDisplayNames(UUID eventTermId) {
        return getOptionalList("/api/bookings/event-term/{id}/participant-names", eventTermId, "participant names");
    }

    private List<String> getOptionalList(String uriTemplate, UUID eventTermId, String label) {
        try {
            List<String> body = restClient.get()
                    .uri(uriTemplate, eventTermId)
                    .retrieve()
                    .body(LIST_STRING);
            return body != null ? body : Collections.emptyList();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("Optional booking endpoint {} not found for term {} — returning empty list", label, eventTermId);
                return Collections.emptyList();
            }
            log.warn("Failed to load {} for term {}: {}", label, eventTermId, e.getMessage());
            return Collections.emptyList();
        } catch (ResourceAccessException e) {
            log.warn("booking-service unavailable while loading {} for term {}", label, eventTermId);
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("booking-service error for {} on term {}: {}", label, eventTermId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
