package com.holidayplanner.paymentservice.client;

import com.holidayplanner.paymentservice.dto.BookingClientResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class BookingServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.booking-service.url:http://localhost:8082}")
    private String bookingServiceUrl;

    public BookingServiceClient(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public List<BookingClientResponse> getBookingsForEventTerm(UUID eventTermId) {
        String url = bookingServiceUrl + "/api/bookings/event-term/" + eventTermId;
        BookingClientResponse[] response = restTemplate.getForObject(url, BookingClientResponse[].class);
        return response == null ? List.of() : Arrays.asList(response);
    }
}
