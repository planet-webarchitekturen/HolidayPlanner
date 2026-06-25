package com.holidayplanner.notificationservice.client;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class BookingServiceClient {

  private static final ParameterizedTypeReference<List<String>> LIST_STRING =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;
  private final String bookingServiceUrl;
  private final String serviceSecret;

  public BookingServiceClient(
      RestClient.Builder restClientBuilder,
      @Value("${services.booking-service.url:http://localhost:8082}") String bookingServiceUrl,
      @Value("${service.secret}") String serviceSecret) {
    this.restClient = restClientBuilder.build();
    this.bookingServiceUrl = bookingServiceUrl;
    this.serviceSecret = serviceSecret;
  }

  public List<String> getParticipantParentEmails(UUID eventTermId) {
    try {
      List<String> result =
          restClient
              .get()
              .uri(bookingServiceUrl + "/api/bookings/event-term/" + eventTermId + "/emails")
              .header("X-Service-Secret", serviceSecret)
              .retrieve()
              .body(LIST_STRING);
      return result != null ? result : Collections.emptyList();
    } catch (RestClientException e) {
      log.warn(
          "Could not fetch participant emails for event term {}: {}", eventTermId, e.getMessage());
      return Collections.emptyList();
    }
  }
}
