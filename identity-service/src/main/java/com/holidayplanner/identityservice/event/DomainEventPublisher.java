package com.holidayplanner.identityservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Service for publishing domain events to Kafka topics.
 * 
 * Handles:
 * - UserRegisteredEvent → holiday-planner.identity.user-registered
 * - UserPhoneUpdatedEvent → holiday-planner.identity.user-phone-updated
 * - FamilyMemberAddedEvent → holiday-planner.identity.family-member-added
 * - FamilyMemberRemovedEvent → holiday-planner.identity.family-member-removed
 * 
 * All events wrapped in DomainEvent envelope with:
 * - eventType, version, timestamp, source, payload
 *
 * Message keys use the plain entity UUID to preserve partition ordering per entity.
 * This ensures ordering per entity (partitioned by key).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish UserRegisteredEvent
     */
    public void publishUserRegistered(UserRegisteredEvent payload) {
        String topic = "holiday-planner.identity.user-registered";
        String key = payload.getUserId().toString();
        DomainEvent event = DomainEvent.of("UserRegistered", payload);
        publishEvent(topic, key, event);
        log.info("Published UserRegisteredEvent for user {} to topic {}", payload.getUserId(), topic);
    }

    /**
     * Publish UserPhoneUpdatedEvent
     */
    public void publishUserPhoneUpdated(UserPhoneUpdatedEvent payload) {
        String topic = "holiday-planner.identity.user-phone-updated";
        String key = payload.getUserId().toString();
        DomainEvent event = DomainEvent.of("UserPhoneUpdated", payload);
        publishEvent(topic, key, event);
        log.info("Published UserPhoneUpdatedEvent for user {} to topic {}", payload.getUserId(), topic);
    }

    /**
     * Publish FamilyMemberAddedEvent
     */
    public void publishFamilyMemberAdded(FamilyMemberAddedEvent payload) {
        String topic = "holiday-planner.identity.family-member-added";
        String key = payload.getFamilyMemberId().toString();
        DomainEvent event = DomainEvent.of("FamilyMemberAdded", payload);
        publishEvent(topic, key, event);
        log.info("Published FamilyMemberAddedEvent for member {} to topic {}", payload.getFamilyMemberId(), topic);
    }

    /**
     * Publish FamilyMemberRemovedEvent
     */
    public void publishFamilyMemberRemoved(FamilyMemberRemovedEvent payload) {
        String topic = "holiday-planner.identity.family-member-removed";
        String key = payload.getFamilyMemberId().toString();
        DomainEvent event = DomainEvent.of("FamilyMemberRemoved", payload);
        publishEvent(topic, key, event);
        log.info("Published FamilyMemberRemovedEvent for member {} to topic {}", payload.getFamilyMemberId(), topic);
    }

    /**
     * Generic event publishing with error handling
     */
    private void publishEvent(String topic, String key, DomainEvent event) {
        try {
            Message<DomainEvent> message = MessageBuilder
                    .withPayload(event)
                    .setHeader(KafkaHeaders.TOPIC, topic)
                    .setHeader("kafka_messageKey", key)
                    .build();

            kafkaTemplate.send(message);
            log.debug("Event published to topic {} with key {}: eventType={}", topic, key, event.getEventType());
        } catch (Exception e) {
            log.error("Failed to publish event to topic {}: {}", topic, e.getMessage(), e);
            // In production, consider: retry logic, dead letter queue, alerting
        }
    }
}
