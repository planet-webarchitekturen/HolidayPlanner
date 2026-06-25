package com.holidayplanner.eventservice.saga;

import com.holidayplanner.eventservice.command.CancellationActor;
import com.holidayplanner.eventservice.port.EventTermEventPublisher;
import com.holidayplanner.eventservice.port.IdentityServicePort;
import com.holidayplanner.shared.kafka.payload.EventTermCancelledPayload;
import com.holidayplanner.shared.model.EventTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventTermCancellationSaga {

    private final IdentityServicePort identityServicePort;
    private final EventTermEventPublisher eventTermEventPublisher;

    public void start(EventTerm term, CancellationActor actor) {
        List<String> caregiverEmails = term.getCaregiverIds().stream()
                .map(id -> {
                    try {
                        return identityServicePort.findCaregiverById(id)
                                .map(c -> c.getEmail())
                                .orElse(null);
                    } catch (Exception e) {
                        log.warn("Could not fetch caregiver {} from identity-service: {}", id, e.getMessage());
                        return null;
                    }
                })
                .filter(email -> email != null)
                .toList();

        String cancelledBy = actor == CancellationActor.SYSTEM ? "SYSTEM" : "EVENT_OWNER";
        EventTermCancelledPayload payload = new EventTermCancelledPayload(
                term.getId(),
                term.getEvent() != null ? term.getEvent().getShortTitle() : null,
                term.getStartDateTime() != null ? term.getStartDateTime().toString() : null,
                term.getEvent() != null ? term.getEvent().getOrganizationId() : null,
                caregiverEmails,
                cancelledBy);

        log.info("Starting event term cancellation saga for term {} cancelled by {} — {} caregiver(s) to notify",
                term.getId(), cancelledBy, caregiverEmails.size());
        eventTermEventPublisher.publishEventTermCancelled(payload);
    }
}
