package com.holidayplanner.organizationservice.client;

import com.holidayplanner.organizationservice.dto.IdentityUserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class IdentityServiceClient {

    private final RestClient restClient;
    private final String identityServiceUrl;

    public IdentityServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.identity-service.url:http://localhost:8083}") String identityServiceUrl) {
        this.restClient = restClientBuilder.build();
        this.identityServiceUrl = identityServiceUrl;
    }

    public IdentityUserResponse getUser(UUID userId) {
        String url = identityServiceUrl + "/api/identity/users/" + userId;
        try {
            return restClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException("Identity service returned an error for user " + userId);
                    })
                    .body(IdentityUserResponse.class);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Identity service unavailable", e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Identity service error: " + e.getMessage(), e);
        }
    }
}
