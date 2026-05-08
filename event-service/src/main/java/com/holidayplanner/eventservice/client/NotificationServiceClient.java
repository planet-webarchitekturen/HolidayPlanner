package com.holidayplanner.eventservice.client;

import com.holidayplanner.eventservice.domain.exception.DownstreamServiceException;
import com.holidayplanner.eventservice.port.NotificationPort;
import com.holidayplanner.shared.model.EmailRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class NotificationServiceClient implements NotificationPort {

    private final RestClient restClient;
    private final String serviceSecret;

    public NotificationServiceClient(
            @Value("${services.notification-service.url:http://localhost:8089}") String notificationServiceUrl,
            @Value("${service.secret:holidayplanner-internal-service-secret}") String serviceSecret) {
        this.restClient = RestClient.builder().baseUrl(notificationServiceUrl).build();
        this.serviceSecret = serviceSecret;
    }

    @Override
    public void sendBulkEmail(List<String> recipients, String subject, String body) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        EmailRequest request = new EmailRequest(null, recipients, subject, body);
        try {
            restClient.post()
                    .uri("/api/notifications/email/bulk")
                    .header("X-Service-Secret", serviceSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new DownstreamServiceException("notification-service", "Unavailable", e);
        } catch (RestClientException e) {
            throw new DownstreamServiceException("notification-service", e.getMessage(), e);
        }
    }
}
