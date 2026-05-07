package com.holidayplanner.identityservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
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
}
