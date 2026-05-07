package com.holidayplanner.paymentservice.client;

import com.holidayplanner.paymentservice.dto.EventTermClientResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class EventServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.event-service.url:http://localhost:8081}")
    private String eventServiceUrl;

    public EventServiceClient(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public EventTermClientResponse getEventTerm(UUID eventTermId) {
        String url = eventServiceUrl + "/api/events/terms/" + eventTermId;
        return restTemplate.getForObject(url, EventTermClientResponse.class);
    }
}
