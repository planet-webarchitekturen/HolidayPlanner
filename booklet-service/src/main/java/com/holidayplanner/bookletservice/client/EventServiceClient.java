package com.holidayplanner.bookletservice.client;

import com.holidayplanner.bookletservice.exception.UpstreamServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

@Component
public class EventServiceClient {

    private final RestClient restClient;
    private final String eventServiceUrl;
    private final String serviceSecret;

    public EventServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.event-service.url:http://localhost:8081}") String eventServiceUrl,
            @Value("${service.secret}") String serviceSecret) {
        this.restClient = restClientBuilder.build();
        this.eventServiceUrl = eventServiceUrl;
        this.serviceSecret = serviceSecret;
    }

    public List<EventDto> getEventsByOrganization(UUID organizationId) {
        try {
            List<EventDto> result = restClient.get()
                    .uri(eventServiceUrl + "/api/events/organization/" + organizationId)
                    .header("X-Service-Secret", serviceSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EventDto>>() {});
            return result != null ? result : List.of();
        } catch (RestClientException e) {
            throw new UpstreamServiceException("Could not fetch events from event-service", e);
        }
    }
}
