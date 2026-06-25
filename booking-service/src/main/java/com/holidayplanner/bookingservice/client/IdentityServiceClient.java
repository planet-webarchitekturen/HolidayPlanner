package com.holidayplanner.bookingservice.client;

import com.holidayplanner.bookingservice.dto.FamilyMemberResponse;
import com.holidayplanner.bookingservice.exception.IdentityServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class IdentityServiceClient {

    private final RestClient restClient;
    private final String identityServiceUrl;
    private final String serviceSecret;

    public IdentityServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${service.secret:holidayplanner-internal-service-secret}") String serviceSecret,
            @Value("${services.identity-service.url:http://localhost:8083}") String identityServiceUrl) {
        this.restClient = restClientBuilder.build();
        this.identityServiceUrl = identityServiceUrl;
        this.serviceSecret = serviceSecret;
    }

    public String getOwnerEmail(UUID familyMemberId) {
        return fetchStringField(familyMemberId, "owner-email", "email", "owner email");
    }

    public String getFamilyMemberDisplayName(UUID familyMemberId) {
        return fetchStringField(familyMemberId, "display-name", "name", "display name");
    }

    public FamilyMemberResponse getFamilyMember(UUID familyMemberId) {
        String url = identityServiceUrl + "/api/identity/family-members/" + familyMemberId;
        try {
            return restClient.get()
                    .uri(url)
                    .headers(headers -> {
                        String token = extractCurrentToken();
                        if (token != null) headers.setBearerAuth(token);
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new IllegalArgumentException("Family member not found or not accessible: " + familyMemberId);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new IdentityServiceException("Identity service returned server error", null);
                    })
                    .body(FamilyMemberResponse.class);
        } catch (IllegalArgumentException | IdentityServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new IdentityServiceException("Identity service unavailable", e);
        } catch (RestClientException e) {
            throw new IdentityServiceException("Identity service error: " + e.getMessage(), e);
        }
    }

    private String fetchStringField(UUID familyMemberId, String endpoint, String field, String label) {
        String url = identityServiceUrl + "/api/identity/family-members/" + familyMemberId + "/" + endpoint;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> response = restClient.get()
                    .uri(url)
                    .header("X-Service-Secret", serviceSecret)
                    .retrieve()
                    .body(Map.class);
            return response != null ? response.get(field) : null;
        } catch (Exception e) {
            log.warn("Could not fetch {} for family member {}: {}", label, familyMemberId, e.getMessage());
            return null;
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
