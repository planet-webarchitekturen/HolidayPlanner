package com.holidayplanner.eventservice.scheduler;

import com.holidayplanner.eventservice.port.EventTermEventPublisher;
import com.holidayplanner.eventservice.port.IdentityServicePort;
import com.holidayplanner.eventservice.query.EventTermQueryService;
import com.holidayplanner.shared.kafka.payload.ParticipantListRequestedPayload;
import com.holidayplanner.shared.model.EventTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DayBeforeNotificationsJob {

    private final EventTermQueryService eventTermQueryService;
    private final IdentityServicePort identityServicePort;
    private final EventTermEventPublisher eventTermEventPublisher;
    private final Clock clock;
    // Runs at 02:15 AM every day, gets all ACTIVE event terms starting the next day and publishes one Kafka event per term.
    @Scheduled(cron = "${event-service.scheduler.day-before-cron:0 15 2 * * *}")
    public void scheduleDayBeforeNotifications() {
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        List<EventTerm> terms = eventTermQueryService.findActiveTermsStartingOn(tomorrow);
        for (EventTerm term : terms) {
            try {
                String eventName = term.getEvent() != null ? term.getEvent().getShortTitle() : "";
                String termDate = term.getStartDateTime() != null ? term.getStartDateTime().toString() : "";
                List<String> caregiverEmails = term.getCaregiverIds().stream()
                        .map(caregiverId -> identityServicePort.findCaregiverById(caregiverId)
                                .map(caregiver -> caregiver.getEmail())
                                .orElseGet(() -> {
                                    log.warn("Caregiver {} not found for event term {}", caregiverId, term.getId());
                                    return null;
                                }))
                        .filter(email -> email != null)
                        .toList();
                if (!caregiverEmails.isEmpty()) {
                    eventTermEventPublisher.publishParticipantListRequested(
                            new ParticipantListRequestedPayload(term.getId(), caregiverEmails, eventName, termDate));
                }
            } catch (Exception e) {
                log.error("Day-before notification failed for event term {}", term.getId(), e);
            }
        }
    }
}
