package com.holidayplanner.eventservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.eventservice.command.CancellationActor;
import com.holidayplanner.eventservice.repository.EventRepository;
import com.holidayplanner.eventservice.repository.EventTermRepository;
import com.holidayplanner.eventservice.saga.EventTermCancellationSaga;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletionStartedPayload;
import com.holidayplanner.shared.model.Event;
import com.holidayplanner.shared.model.EventTerm;
import com.holidayplanner.shared.model.EventTermStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDeletionStartedConsumer {

    private final ObjectMapper objectMapper;
    private final EventRepository eventRepository;
    private final EventTermRepository eventTermRepository;
    private final EventTermCancellationSaga eventTermCancellationSaga;

    @KafkaListener(
            topics = "holiday-planner.organization.deletion-started",
            groupId = "event-service-org-deletion-started"
    )
    public void consume(String message) {
        try {
            KafkaEnvelope<OrganizationDeletionStartedPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<OrganizationDeletionStartedPayload>>() {});
            OrganizationDeletionStartedPayload payload = envelope.getPayload();

            log.info("Processing OrganizationDeletionStarted for organization: {}", payload.getOrganizationId());

            // Find all events for this organization
            List<Event> events = eventRepository.findByOrganizationId(payload.getOrganizationId());

            // For each event, find and cancel all ACTIVE terms
            for (Event event : events) {
                List<EventTerm> activeTerms = eventTermRepository.findByEvent_IdAndStatus(
                        event.getId(), EventTermStatus.ACTIVE);
                for (EventTerm term : activeTerms) {
                    // Start cancellation saga for this term
                    log.info("Cancelling event term {} as part of organization deletion", term.getId());
                    eventTermCancellationSaga.start(term, CancellationActor.SYSTEM);
                }
            }

            log.info("Completed OrganizationDeletionStarted processing for organization: {}", payload.getOrganizationId());
        } catch (Exception e) {
            log.error("Failed to process OrganizationDeletionStarted event: {}", e.getMessage(), e);
        }
    }
}