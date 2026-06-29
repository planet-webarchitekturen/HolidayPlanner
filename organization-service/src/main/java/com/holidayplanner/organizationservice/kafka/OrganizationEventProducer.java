package com.holidayplanner.organizationservice.kafka;

import com.holidayplanner.organizationservice.outbox.OrganizationOutboxService;
import com.holidayplanner.shared.kafka.payload.OrganizationCreatedPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletedPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletionRolledBackPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletionStartedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Publishes organization domain events via the transactional outbox. Each method records the event in
 * the same transaction as the state change (see {@link OrganizationOutboxService#record}); the
 * {@link com.holidayplanner.organizationservice.outbox.OrganizationOutboxRelay} delivers it to Kafka
 * asynchronously. This replaces the previous direct {@code KafkaTemplate.send}, whose failures were
 * only logged — meaning a committed state change could silently lose its event (dual-write problem).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationEventProducer {

    private static final String AGGREGATE_TYPE = "Organization";
    private static final String TOPIC_CREATED = "holiday-planner.organization.created";
    private static final String TOPIC_DELETION_STARTED = "holiday-planner.organization.deletion-started";
    private static final String TOPIC_DELETED = "holiday-planner.organization.deleted";
    private static final String TOPIC_DELETION_ROLLED_BACK = "holiday-planner.organization.deletion-rolled-back";

    private final OrganizationOutboxService outboxService;

    public void publishOrganizationCreated(OrganizationCreatedPayload payload) {
        outboxService.record(AGGREGATE_TYPE, payload.getOrganizationId().toString(),
                "OrganizationCreated", TOPIC_CREATED, payload);
    }

    public void publishOrganizationDeletionStarted(OrganizationDeletionStartedPayload payload) {
        outboxService.record(AGGREGATE_TYPE, payload.getOrganizationId().toString(),
                "OrganizationDeletionStarted", TOPIC_DELETION_STARTED, payload);
    }

    public void publishOrganizationDeleted(OrganizationDeletedPayload payload) {
        outboxService.record(AGGREGATE_TYPE, payload.getOrganizationId().toString(),
                "OrganizationDeleted", TOPIC_DELETED, payload);
    }

    public void publishOrganizationDeletionRolledBack(OrganizationDeletionRolledBackPayload payload) {
        outboxService.record(AGGREGATE_TYPE, payload.getOrganizationId().toString(),
                "OrganizationDeletionRolledBack", TOPIC_DELETION_ROLLED_BACK, payload);
    }
}
