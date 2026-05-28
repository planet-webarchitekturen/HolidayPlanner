package com.holidayplanner.identityservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.FamilyMemberAddedPayload;
import com.holidayplanner.shared.kafka.payload.FamilyMemberRemovedPayload;
import com.holidayplanner.shared.kafka.payload.UserDeletedPayload;
import com.holidayplanner.shared.kafka.payload.UserUpdatedPayload;
import com.holidayplanner.shared.kafka.payload.UserRegisteredPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishUserRegistered(UserRegisteredPayload payload) {
        try {
            KafkaEnvelope<UserRegisteredPayload> envelope = new KafkaEnvelope<>(
                    "UserRegistered", "1",
                    LocalDateTime.now().toString(),
                    "identity-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.identity.user-registered",
                    payload.getUserId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish UserRegistered event", e);
        }
    }

    public void publishUserUpdated(UserUpdatedPayload payload) {
        try {
            KafkaEnvelope<UserUpdatedPayload> envelope = new KafkaEnvelope<>(
                    "UserUpdated", "1",
                    LocalDateTime.now().toString(),
                    "identity-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.identity.user-updated",
                    payload.getUserId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish UserUpdated event", e);
        }
    }

    public void publishUserDeleted(UserDeletedPayload payload) {
        try {
            KafkaEnvelope<UserDeletedPayload> envelope = new KafkaEnvelope<>(
                    "UserDeleted", "1",
                    LocalDateTime.now().toString(),
                    "identity-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.identity.user-deleted",
                    payload.getUserId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish UserDeleted event", e);
        }
    }

    public void publishFamilyMemberAdded(FamilyMemberAddedPayload payload) {
        try {
            KafkaEnvelope<FamilyMemberAddedPayload> envelope = new KafkaEnvelope<>(
                    "FamilyMemberAdded", "1",
                    LocalDateTime.now().toString(),
                    "identity-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.identity.family-member-added",
                    payload.getUserId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish FamilyMemberAdded event", e);
        }
    }

    public void publishFamilyMemberRemoved(FamilyMemberRemovedPayload payload) {
        try {
            KafkaEnvelope<FamilyMemberRemovedPayload> envelope = new KafkaEnvelope<>(
                    "FamilyMemberRemoved", "1",
                    LocalDateTime.now().toString(),
                    "identity-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.identity.family-member-removed",
                    payload.getUserId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish FamilyMemberRemoved event", e);
        }
    }
}
