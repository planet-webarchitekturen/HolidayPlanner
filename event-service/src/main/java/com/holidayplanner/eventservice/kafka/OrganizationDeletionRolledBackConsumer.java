package com.holidayplanner.eventservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.eventservice.port.EventTermEventPublisher;
import com.holidayplanner.eventservice.repository.EventTermRepository;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.EventTermRestoredPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletionRolledBackPayload;
import com.holidayplanner.shared.model.EventTerm;
import com.holidayplanner.shared.model.EventTermStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDeletionRolledBackConsumer {

    private final ObjectMapper objectMapper;
    private final EventTermRepository eventTermRepository;
    private final EventTermEventPublisher eventTermEventPublisher;

    @KafkaListener(
            topics = "holiday-planner.organization.deletion-rolled-back",
            groupId = "event-service-org-deletion-rolled-back"
    )
    @Transactional
    public void consume(String message) {
        try {
            KafkaEnvelope<OrganizationDeletionRolledBackPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<OrganizationDeletionRolledBackPayload>>() {});
            OrganizationDeletionRolledBackPayload payload = envelope.getPayload();

            log.info("Processing OrganizationDeletionRolledBack for organization: {}", payload.getOrganizationId());

            List<EventTerm> sagaTerms = eventTermRepository
                    .findCancelledBySagaForOrganization(payload.getOrganizationId());

            for (EventTerm term : sagaTerms) {
                term.setStatus(EventTermStatus.ACTIVE);
                term.setCancelledBySaga(false);
                eventTermRepository.save(term);

                eventTermEventPublisher.publishEventTermRestored(
                        new EventTermRestoredPayload(term.getId(), payload.getOrganizationId()));

                log.info("Restored event term {} to ACTIVE (org rollback)", term.getId());
            }

            log.info("Restored {} event term(s) for organization {}", sagaTerms.size(), payload.getOrganizationId());
        } catch (Exception e) {
            log.error("Failed to process OrganizationDeletionRolledBack event: {}", e.getMessage(), e);
        }
    }
}
