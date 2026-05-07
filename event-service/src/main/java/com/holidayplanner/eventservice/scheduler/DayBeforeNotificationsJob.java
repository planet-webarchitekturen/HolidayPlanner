package com.holidayplanner.eventservice.scheduler;

import com.holidayplanner.eventservice.port.BookingServicePort;
import com.holidayplanner.eventservice.port.EventTermEventPublisher;
import com.holidayplanner.eventservice.port.IdentityServicePort;
import com.holidayplanner.eventservice.query.EventTermQueryService;
import com.holidayplanner.shared.kafka.payload.ParticipantListRequestedPayload;
import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.EventTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DayBeforeNotificationsJob {

    private final EventTermQueryService eventTermQueryService;
    private final BookingServicePort bookingServicePort;
    private final IdentityServicePort identityServicePort;
    private final EventTermEventPublisher eventTermEventPublisher;

    @Scheduled(cron = "${event-service.scheduler.day-before-cron:0 15 2 * * *}")
    public void scheduleDayBeforeNotifications() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<EventTerm> terms = eventTermQueryService.findActiveTermsStartingOn(tomorrow);
        for (EventTerm term : terms) {
            try {
                List<String> participantNames = bookingServicePort.getParticipantDisplayNames(term.getId());
                String eventName = term.getEvent() != null ? term.getEvent().getShortTitle() : "";
                String termDate = term.getStartDateTime() != null ? term.getStartDateTime().toString() : "";
                for (UUID caregiverId : term.getCaregiverIds()) {
                    identityServicePort.findCaregiverById(caregiverId).ifPresentOrElse(
                            cg -> publishForCaregiver(term, cg, eventName, termDate, participantNames),
                            () -> log.warn("Caregiver {} not found for event term {}", caregiverId, term.getId()));
                }
            } catch (Exception e) {
                log.error("Day-before notification failed for event term {}", term.getId(), e);
            }
        }
    }

    private void publishForCaregiver(
            EventTerm term,
            Caregiver caregiver,
            String eventName,
            String termDate,
            List<String> participantNames) {
        ParticipantListRequestedPayload payload = new ParticipantListRequestedPayload(
                term.getId(),
                caregiver.getEmail(),
                eventName,
                termDate,
                participantNames);
        eventTermEventPublisher.publishParticipantListRequested(payload);
    }
}
