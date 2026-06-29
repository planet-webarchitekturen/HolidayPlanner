package com.holidayplanner.organizationservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletedPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the outbox writer: a recorded event lands in the table with the right routing
 * metadata and a self-contained, serialized {@code KafkaEnvelope} payload.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationOutboxServiceTest {

    @Mock private OrganizationOutboxEventRepository repository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private OrganizationOutboxService service;

    @Test
    void record_persistsEventWithRoutingMetadataAndSerializedEnvelope() {
        UUID orgId = UUID.randomUUID();
        OrganizationDeletedPayload payload = new OrganizationDeletedPayload(orgId, "Demo Org");

        service.record("Organization", orgId.toString(), "OrganizationDeleted",
                "holiday-planner.organization.deleted", payload);

        ArgumentCaptor<OrganizationOutboxEvent> captor = ArgumentCaptor.forClass(OrganizationOutboxEvent.class);
        verify(repository).save(captor.capture());
        OrganizationOutboxEvent saved = captor.getValue();

        assertThat(saved.getAggregateType()).isEqualTo("Organization");
        assertThat(saved.getAggregateId()).isEqualTo(orgId.toString());
        assertThat(saved.getEventType()).isEqualTo("OrganizationDeleted");
        assertThat(saved.getTopic()).isEqualTo("holiday-planner.organization.deleted");
        assertThat(saved.getPartitionKey()).isEqualTo(orgId.toString());
        assertThat(saved.isProcessed()).isFalse();
        assertThat(saved.getProcessedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        // The payload column is a ready-to-publish envelope: it carries the event type, the source,
        // and the inner payload's organization id.
        assertThat(saved.getPayload())
                .contains("\"eventType\":\"OrganizationDeleted\"")
                .contains("\"source\":\"organization-service\"")
                .contains(orgId.toString());
    }
}
