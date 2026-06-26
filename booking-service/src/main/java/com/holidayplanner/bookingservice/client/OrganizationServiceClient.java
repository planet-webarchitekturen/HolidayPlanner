package com.holidayplanner.bookingservice.client;

import com.holidayplanner.bookingservice.dto.OrganizationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/** HTTP client for reading organization data (e.g. bookingStartTime) from organization-service. */
@Slf4j
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

    /** Fetch an organization, or null if it cannot be resolved (booking window check then skipped). */
    public OrganizationDto getOrganization(UUID organizationId) {
        if (organizationId == null) {
            return null;
        }
        try {
            return restClient.get()
                    .uri(organizationServiceUrl + "/api/organizations/" + organizationId)
                    .header("X-Service-Secret", serviceSecret)
                    .retrieve()
                    .body(OrganizationDto.class);
        } catch (Exception e) {
            log.warn("Could not fetch organization {}: {}", organizationId, e.getMessage());
            return null;
        }
    }
}
