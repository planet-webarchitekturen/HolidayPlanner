package com.holidayplanner.bookingservice.client;

import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.exception.EventServiceException;
import com.holidayplanner.bookingservice.exception.EventTermNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
public class EventServiceClient {

    private final RestClient restClient;
    private final String eventServiceUrl;

    public EventServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.event-service.url:http://localhost:8081}") String eventServiceUrl) {
        this.restClient = restClientBuilder.build();
        this.eventServiceUrl = eventServiceUrl;
    }

    public EventTermDetailResponse getEventTerm(UUID eventTermId) {
        String url = eventServiceUrl + "/api/events/terms/" + eventTermId;
        try {
            return restClient.get()
                    .uri(url)
                    .headers(headers -> {
                        String token = extractCurrentToken();
                        if (token != null) {
                            headers.setBearerAuth(token);
                        }
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new EventTermNotFoundException(eventTermId);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new EventServiceException("Event service returned server error", null);
                    })
                    .body(EventTermDetailResponse.class);
        } catch (EventTermNotFoundException | EventServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new EventServiceException("Event service unavailable", e);
        } catch (RestClientException e) {
            throw new EventServiceException("Event service error: " + e.getMessage(), e);
        }
    }

    private String extractCurrentToken() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                return header.substring(7);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
