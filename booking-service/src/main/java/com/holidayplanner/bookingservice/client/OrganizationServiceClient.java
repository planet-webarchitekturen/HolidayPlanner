package com.holidayplanner.bookingservice.client;

import com.holidayplanner.bookingservice.dto.OrganizationResponse;
import com.holidayplanner.bookingservice.exception.OrganizationServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class OrganizationServiceClient {

    private final RestClient restClient;
    private final String organizationServiceUrl;
    private final String serviceSecret;

    public OrganizationServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.organization-service.url:http://localhost:8084}") String organizationServiceUrl,
            @Value("${service.secret:holidayplanner-internal-service-secret}") String serviceSecret) {
        this.restClient = restClientBuilder.build();
        this.organizationServiceUrl = organizationServiceUrl;
        this.serviceSecret = serviceSecret;
    }

    public OrganizationResponse getOrganization(UUID organizationId) {
        String url = organizationServiceUrl + "/api/organizations/" + organizationId;
        try {
            return restClient.get()
                    .uri(url)
                    .header("X-Service-Secret", serviceSecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new IllegalStateException("Organization not found: " + organizationId);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new OrganizationServiceException("Organization service returned server error", null);
                    })
                    .body(OrganizationResponse.class);
        } catch (IllegalStateException | OrganizationServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new OrganizationServiceException("Organization service unavailable", e);
        } catch (RestClientException e) {
            throw new OrganizationServiceException("Organization service error: " + e.getMessage(), e);
        }
    }
}
