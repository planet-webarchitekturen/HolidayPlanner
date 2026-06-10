package com.holidayplanner.identityservice.kafka;

import com.holidayplanner.shared.kafka.payload.FamilyMemberAddedPayload;
import com.holidayplanner.shared.kafka.payload.FamilyMemberRemovedPayload;
import com.holidayplanner.shared.kafka.payload.UserDeletedPayload;
import com.holidayplanner.shared.kafka.payload.UserUpdatedPayload;
import com.holidayplanner.shared.kafka.payload.UserRegisteredPayload;
import com.holidayplanner.identityservice.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Publishes identity domain events.
 *
 * Events are not sent to Kafka directly here; they are recorded in the
 * transactional outbox so they commit atomically with the command that produced
 * them (see {@link OutboxService} and {@code OutboxRelay}). This class owns the
 * mapping from a payload to its event type, topic, and partition key — the single
 * place that knows the identity-service topic structure.
 */
@Service
@RequiredArgsConstructor
public class IdentityEventProducer {

    private static final String TOPIC_USER_REGISTERED = "holiday-planner.identity.user-registered";
    private static final String TOPIC_USER_UPDATED = "holiday-planner.identity.user-updated";
    private static final String TOPIC_USER_DELETED = "holiday-planner.identity.user-deleted";
    private static final String TOPIC_FAMILY_MEMBER_ADDED = "holiday-planner.identity.family-member-added";
    private static final String TOPIC_FAMILY_MEMBER_REMOVED = "holiday-planner.identity.family-member-removed";

    private final OutboxService outboxService;

    public void publishUserRegistered(UserRegisteredPayload payload) {
        outboxService.record("User", payload.getUserId().toString(),
                "UserRegistered", TOPIC_USER_REGISTERED, payload);
    }

    public void publishUserUpdated(UserUpdatedPayload payload) {
        outboxService.record("User", payload.getUserId().toString(),
                "UserUpdated", TOPIC_USER_UPDATED, payload);
    }

    public void publishUserDeleted(UserDeletedPayload payload) {
        outboxService.record("User", payload.getUserId().toString(),
                "UserDeleted", TOPIC_USER_DELETED, payload);
    }

    public void publishFamilyMemberAdded(FamilyMemberAddedPayload payload) {
        outboxService.record("FamilyMember", payload.getUserId().toString(),
                "FamilyMemberAdded", TOPIC_FAMILY_MEMBER_ADDED, payload);
    }

    public void publishFamilyMemberRemoved(FamilyMemberRemovedPayload payload) {
        outboxService.record("FamilyMember", payload.getUserId().toString(),
                "FamilyMemberRemoved", TOPIC_FAMILY_MEMBER_REMOVED, payload);
    }
}
