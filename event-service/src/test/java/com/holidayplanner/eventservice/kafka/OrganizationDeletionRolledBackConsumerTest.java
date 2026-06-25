package com.holidayplanner.eventservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.eventservice.port.EventTermEventPublisher;
import com.holidayplanner.eventservice.repository.EventTermRepository;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.EventTermRestoredPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletionRolledBackPayload;
import com.holidayplanner.shared.model.Event;
import com.holidayplanner.shared.model.EventTerm;
import com.holidayplanner.shared.model.EventTermStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationDeletionRolledBackConsumerTest {

    @Mock
    private EventTermRepository eventTermRepository;
    @Mock
    private EventTermEventPublisher eventTermEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrganizationDeletionRolledBackConsumer consumer() {
        return new OrganizationDeletionRolledBackConsumer(objectMapper, eventTermRepository, eventTermEventPublisher);
    }

    private String rolledBackMessage(UUID organizationId) throws Exception {
        OrganizationDeletionRolledBackPayload payload =
                new OrganizationDeletionRolledBackPayload(organizationId, "Test Org");
        KafkaEnvelope<OrganizationDeletionRolledBackPayload> envelope = new KafkaEnvelope<>(
                "OrganizationDeletionRolledBack", "1", "2025-01-01T00:00:00",
                "organization-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }

    private EventTerm sagaTerm(UUID termId, UUID orgId) {
        Event event = new Event();
        event.setOrganizationId(orgId);
        EventTerm term = new EventTerm();
        term.setId(termId);
        term.setEvent(event);
        term.setStatus(EventTermStatus.CANCELLED);
        term.setCancelledBySaga(true);
        return term;
    }

    // ── happy-path restoration ────────────────────────────────────────────────

    @Test
    void rolledBack_singleTerm_isRestoredToActive() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        EventTerm term = sagaTerm(termId, orgId);
        when(eventTermRepository.findCancelledBySagaForOrganization(orgId)).thenReturn(List.of(term));
        when(eventTermRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer().consume(rolledBackMessage(orgId));

        assertThat(term.getStatus()).isEqualTo(EventTermStatus.ACTIVE);
        assertThat(term.isCancelledBySaga()).isFalse();
    }

    @Test
    void rolledBack_multipleTerms_allRestoredAndSaved() throws Exception {
        UUID orgId = UUID.randomUUID();
        EventTerm term1 = sagaTerm(UUID.randomUUID(), orgId);
        EventTerm term2 = sagaTerm(UUID.randomUUID(), orgId);
        when(eventTermRepository.findCancelledBySagaForOrganization(orgId)).thenReturn(List.of(term1, term2));
        when(eventTermRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer().consume(rolledBackMessage(orgId));

        assertThat(term1.getStatus()).isEqualTo(EventTermStatus.ACTIVE);
        assertThat(term2.getStatus()).isEqualTo(EventTermStatus.ACTIVE);
        verify(eventTermRepository, times(2)).save(any());
    }

    @Test
    void rolledBack_publishesEventTermRestoredPerTerm() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        when(eventTermRepository.findCancelledBySagaForOrganization(orgId))
                .thenReturn(List.of(sagaTerm(termId, orgId)));
        when(eventTermRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer().consume(rolledBackMessage(orgId));

        ArgumentCaptor<EventTermRestoredPayload> captor =
                ArgumentCaptor.forClass(EventTermRestoredPayload.class);
        verify(eventTermEventPublisher).publishEventTermRestored(captor.capture());
        assertThat(captor.getValue().getEventTermId()).isEqualTo(termId);
        assertThat(captor.getValue().getOrganizationId()).isEqualTo(orgId);
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    void rolledBack_whenNoSagaTermsExist_publishesNothingAndSavesNothing() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(eventTermRepository.findCancelledBySagaForOrganization(orgId)).thenReturn(List.of());

        consumer().consume(rolledBackMessage(orgId));

        verify(eventTermRepository, never()).save(any());
        verifyNoInteractions(eventTermEventPublisher);
    }

    @Test
    void malformedMessage_isSwallowedWithNoSideEffects() {
        consumer().consume("not-json");

        verifyNoInteractions(eventTermRepository, eventTermEventPublisher);
    }
}
