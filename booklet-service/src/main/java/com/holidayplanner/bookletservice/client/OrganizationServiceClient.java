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
public class OrganizationServiceClient {

    private final RestClient restClient;
    private final String organizationServiceUrl;

    public OrganizationServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.organization-service.url:http://localhost:8084}") String organizationServiceUrl) {
        this.restClient = restClientBuilder.build();
        this.organizationServiceUrl = organizationServiceUrl;
    }

    public OrganizationDto getOrganization(UUID organizationId) {
        return get("/api/organizations/" + organizationId, OrganizationDto.class, "organization");
    }

    public List<TeamMemberDto> getTeamMembers(UUID organizationId) {
        return getList("/api/organizations/" + organizationId + "/team-members",
                new ParameterizedTypeReference<List<TeamMemberDto>>() {}, "team members");
    }

    public List<SponsorDto> getSponsors(UUID organizationId) {
        return getList("/api/organizations/" + organizationId + "/sponsors",
                new ParameterizedTypeReference<List<SponsorDto>>() {}, "sponsors");
    }

    private <T> T get(String path, Class<T> responseType, String resourceName) {
        try {
            T result = restClient.get()
                    .uri(organizationServiceUrl + path)
                    .retrieve()
                    .body(responseType);
            if (result == null) {
                throw new UpstreamServiceException("Empty " + resourceName + " response from organization-service", null);
            }
            return result;
        } catch (RestClientException e) {
            throw new UpstreamServiceException("Could not fetch " + resourceName + " from organization-service", e);
        }
    }

    private <T> List<T> getList(String path, ParameterizedTypeReference<List<T>> responseType, String resourceName) {
        try {
            List<T> result = restClient.get()
                    .uri(organizationServiceUrl + path)
                    .retrieve()
                    .body(responseType);
            return result != null ? result : List.of();
        } catch (RestClientException e) {
            throw new UpstreamServiceException("Could not fetch " + resourceName + " from organization-service", e);
        }
    }
}
