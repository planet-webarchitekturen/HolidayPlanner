package com.holidayplanner.paymentservice.client;

import com.holidayplanner.paymentservice.dto.BookingClientResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class BookingServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.booking-service.url:http://localhost:8082}")
    private String bookingServiceUrl;

    @Value("${service.secret:holidayplanner-internal-service-secret}")
    private String serviceSecret;

    public BookingServiceClient(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public List<BookingClientResponse> getBookingsForEventTerm(UUID eventTermId) {
        String url = bookingServiceUrl + "/api/bookings/event-term/" + eventTermId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Secret", serviceSecret);
        List<BookingClientResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<BookingClientResponse>>() {}).getBody();
        return response == null ? List.of() : response;
    }
}
