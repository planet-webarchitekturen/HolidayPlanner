package com.holidayplanner.notificationservice.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BookletServiceClient {

  private final RestClient restClient;
  private final String bookletServiceUrl;
  private final String serviceSecret;

  public BookletServiceClient(
      RestClient.Builder restClientBuilder,
      @Value("${services.booklet-service.url:http://localhost:8087}") String bookletServiceUrl,
      @Value("${service.secret}") String serviceSecret) {
    this.restClient = restClientBuilder.build();
    this.bookletServiceUrl = bookletServiceUrl;
    this.serviceSecret = serviceSecret;
  }

  public byte[] getParticipantListPdf(UUID eventTermId) {
    byte[] result =
        restClient
            .get()
            .uri(bookletServiceUrl + "/api/booklets/participant-list/" + eventTermId)
            .header("X-Service-Secret", serviceSecret)
            .retrieve()
            .body(byte[].class);
    return result != null ? result : new byte[0];
  }
}
