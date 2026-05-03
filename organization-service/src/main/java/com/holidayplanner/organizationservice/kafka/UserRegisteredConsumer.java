package com.holidayplanner.organizationservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.organizationservice.query.OrganizationQueryService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.UserRegisteredPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final OrganizationQueryService organizationQueryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "holiday-planner.identity.user-registered", groupId = "organization-service")
    public void consume(String message) {
        try {
            KafkaEnvelope<UserRegisteredPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<UserRegisteredPayload>>() {});
            UserRegisteredPayload payload = envelope.getPayload();

            boolean organizationExists = organizationQueryService.organizationExists(payload.getOrganizationId());
            if (!organizationExists) {
                log.warn("Received UserRegistered event for unknown organization {}", payload.getOrganizationId());
                return;
            }

            log.info("Registered user {} belongs to organization {}", payload.getUserId(), payload.getOrganizationId());
        } catch (Exception e) {
            log.error("Failed to process UserRegistered event: {}", e.getMessage());
        }
    }
}
