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

    public NotificationServiceClient(
            @Value("${services.notification-service.url:http://localhost:8086}") String notificationServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(notificationServiceUrl).build();
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
