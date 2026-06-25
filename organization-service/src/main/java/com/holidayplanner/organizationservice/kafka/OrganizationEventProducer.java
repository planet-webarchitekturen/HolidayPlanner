package com.holidayplanner.organizationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.OrganizationCreatedPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletionStartedPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishOrganizationCreated(OrganizationCreatedPayload payload) {
        try {
            KafkaEnvelope<OrganizationCreatedPayload> envelope = new KafkaEnvelope<>(
                    "OrganizationCreated", "1",
                    LocalDateTime.now().toString(),
                    "organization-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.organization.created",
                    payload.getOrganizationId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish OrganizationCreated event", e);
        }
    }

    public void publishOrganizationDeletionStarted(OrganizationDeletionStartedPayload payload) {
        try {
            KafkaEnvelope<OrganizationDeletionStartedPayload> envelope = new KafkaEnvelope<>(
                    "OrganizationDeletionStarted", "1",
                    LocalDateTime.now().toString(),
                    "organization-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.organization.deletion-started",
                    payload.getOrganizationId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish OrganizationDeletionStarted event", e);
        }
    }

    public void publishOrganizationDeleted(OrganizationDeletedPayload payload) {
        try {
            KafkaEnvelope<OrganizationDeletedPayload> envelope = new KafkaEnvelope<>(
                    "OrganizationDeleted", "1",
                    LocalDateTime.now().toString(),
                    "organization-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.organization.deleted",
                    payload.getOrganizationId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish OrganizationDeleted event", e);
        }
    }
}