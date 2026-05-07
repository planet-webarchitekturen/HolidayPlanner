package com.holidayplanner.eventservice.client;

import com.holidayplanner.eventservice.domain.exception.DownstreamServiceException;
import com.holidayplanner.eventservice.port.IdentityServicePort;
import com.holidayplanner.shared.model.Caregiver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;
import java.util.UUID;

@Component
public class IdentityServiceClient implements IdentityServicePort {

    private final RestClient restClient;

    public IdentityServiceClient(
            @Value("${services.identity-service.url:http://localhost:8083}") String identityServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(identityServiceUrl).build();
    }

    @Override
    public Optional<Caregiver> findCaregiverById(UUID caregiverId) {
        try {
            Caregiver caregiver = restClient.get()
                    .uri("/api/identity/caregivers/{id}", caregiverId)
                    .retrieve()
                    .body(Caregiver.class);
            return Optional.ofNullable(caregiver);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new DownstreamServiceException("identity-service", e.getMessage(), e);
        } catch (ResourceAccessException e) {
            throw new DownstreamServiceException("identity-service", "Unavailable", e);
        } catch (RestClientException e) {
            throw new DownstreamServiceException("identity-service", e.getMessage(), e);
        }
    }
}
