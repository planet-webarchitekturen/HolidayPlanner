package com.holidayplanner.eventservice.saga;

import com.holidayplanner.eventservice.command.CancellationActor;
import com.holidayplanner.eventservice.port.EventTermEventPublisher;
import com.holidayplanner.eventservice.repository.CaregiverRepository;
import com.holidayplanner.shared.kafka.payload.EventTermCancelledPayload;
import com.holidayplanner.shared.model.EventTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventTermCancellationSaga {

    private final CaregiverRepository caregiverRepository;
    private final EventTermEventPublisher eventTermEventPublisher;

    public void start(EventTerm term, CancellationActor actor) {
       List<String> caregiverEmails = new ArrayList<>();
for (UUID caregiverId : term.getCaregiverIds()) {
    caregiverRepository.findById(caregiverId)
            .map(caregiver -> caregiver.getEmail())
            .ifPresent(caregiverEmails::add);
}

        String cancelledBy = actor == CancellationActor.SYSTEM ? "SYSTEM" : "EVENT_OWNER";
        EventTermCancelledPayload payload = new EventTermCancelledPayload(
                term.getId(),
                term.getEvent() != null ? term.getEvent().getShortTitle() : null,
                term.getStartDateTime() != null ? term.getStartDateTime().toString() : null,
                term.getEvent() != null ? term.getEvent().getOrganizationId() : null,
                caregiverEmails,
                cancelledBy);

        log.info("Starting event term cancellation saga for term {} cancelled by {}", term.getId(), cancelledBy);
        eventTermEventPublisher.publishEventTermCancelled(payload);
    }
}
