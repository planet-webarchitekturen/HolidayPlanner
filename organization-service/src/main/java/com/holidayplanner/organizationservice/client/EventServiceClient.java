package com.holidayplanner.organizationservice.client;

import com.holidayplanner.shared.security.ServiceAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

    public void deleteEventsByOrganization(UUID organizationId) {
        String url = eventServiceUrl + "/api/events/organization/" + organizationId;
        try {
            restClient.delete()
                    .uri(url)
                    .header(ServiceAuthenticationFilter.HEADER, serviceSecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException(
                                "Event service returned an error while deleting events for org " + organizationId);
                    })
                    .toBodilessEntity();
        } catch (IllegalStateException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Event service unavailable", e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Event service error: " + e.getMessage(), e);
        }
    }
}
