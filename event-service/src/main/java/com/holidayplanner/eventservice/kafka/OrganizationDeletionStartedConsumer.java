package com.holidayplanner.eventservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.eventservice.command.CancellationActor;
import com.holidayplanner.eventservice.command.EventTermCommandService;
import com.holidayplanner.eventservice.repository.EventRepository;
import com.holidayplanner.eventservice.repository.EventTermRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDeletionStartedConsumer {

    private final ObjectMapper objectMapper;
    private final EventRepository eventRepository;
    private final EventTermRepository eventTermRepository;
    private final EventTermCommandService eventTermCommandService;

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

            List<Event> events = eventRepository.findByOrganizationId(payload.getOrganizationId());

            for (Event event : events) {
                List<EventTerm> activeTerms = eventTermRepository.findByEvent_IdAndStatus(
                        event.getId(), EventTermStatus.ACTIVE);
                for (EventTerm term : activeTerms) {
                    log.info("Cancelling event term {} as part of organization deletion", term.getId());
                    // Route through changeEventTermStatus so the DB status is persisted
                    // and cancelledBySaga is stamped on the term before the saga fires.
                    eventTermCommandService.changeEventTermStatus(
                            term.getId(), EventTermStatus.CANCELLED, CancellationActor.SYSTEM);
                }
            }

            log.info("Completed OrganizationDeletionStarted processing for organization: {}", payload.getOrganizationId());
        } catch (Exception e) {
            log.error("Failed to process OrganizationDeletionStarted event: {}", e.getMessage(), e);
        }
    }
}