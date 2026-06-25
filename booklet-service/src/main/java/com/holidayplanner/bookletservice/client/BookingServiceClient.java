package com.holidayplanner.bookletservice.client;

import com.holidayplanner.bookletservice.exception.UpstreamServiceException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BookingServiceClient {

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

  public List<String> getParticipantDisplayNames(UUID eventTermId) {
    try {
      List<String> result =
          restClient
              .get()
              .uri(
                  bookingServiceUrl
                      + "/api/bookings/event-term/"
                      + eventTermId
                      + "/participant-names")
              .header("X-Service-Secret", serviceSecret)
              .retrieve()
              .body(new ParameterizedTypeReference<List<String>>() {});
      return result != null ? result : List.of();
    } catch (RestClientException e) {
      throw new UpstreamServiceException(
          "Could not fetch participant names from booking-service", e);
    }
  }
}
