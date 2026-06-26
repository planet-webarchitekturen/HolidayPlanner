package com.holidayplanner.eventservice.scheduler;

import com.holidayplanner.eventservice.port.EventTermEventPublisher;
import com.holidayplanner.eventservice.port.IdentityServicePort;
import com.holidayplanner.eventservice.query.EventTermQueryService;
import com.holidayplanner.shared.kafka.payload.ParticipantListRequestedPayload;
import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.Event;
import com.holidayplanner.shared.model.EventTerm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DayBeforeNotificationsJobTest {

    private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");

    @Mock
    private EventTermQueryService eventTermQueryService;
    @Mock
    private IdentityServicePort identityServicePort;
    @Mock
    private EventTermEventPublisher eventTermEventPublisher;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), VIENNA);

    private DayBeforeNotificationsJob job;

    @BeforeEach
    void setUp() {
        job = new DayBeforeNotificationsJob(
                eventTermQueryService, identityServicePort, eventTermEventPublisher, fixedClock);
    }

    private EventTerm termWithCaregivers(UUID termId, List<UUID> caregiverIds, LocalDateTime start, String eventTitle) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setShortTitle(eventTitle);
        EventTerm t = new EventTerm();
        t.setId(termId);
        t.setEvent(event);
        t.setStartDateTime(start);
        t.getCaregiverIds().addAll(caregiverIds);
        return t;
    }

    @Test
    void publishesParticipantListWithCaregiverEmailsForTomorrowsTerms() {
        LocalDate tomorrow = LocalDate.now(fixedClock).plusDays(1);
        UUID termId = UUID.randomUUID();
        UUID caregiverId1 = UUID.randomUUID();
        UUID caregiverId2 = UUID.randomUUID();
        LocalDateTime start = tomorrow.atTime(9, 0);
        EventTerm term = termWithCaregivers(termId, List.of(caregiverId1, caregiverId2), start, "Bike Adventure");

        Caregiver caregiver1 = new Caregiver();
        caregiver1.setId(caregiverId1);
        caregiver1.setEmail("caregiver1@example.test");
        Caregiver caregiver2 = new Caregiver();
        caregiver2.setId(caregiverId2);
        caregiver2.setEmail("caregiver2@example.test");

        when(eventTermQueryService.findActiveTermsStartingOn(tomorrow)).thenReturn(List.of(term));
        when(identityServicePort.findCaregiverById(caregiverId1)).thenReturn(Optional.of(caregiver1));
        when(identityServicePort.findCaregiverById(caregiverId2)).thenReturn(Optional.of(caregiver2));

        job.scheduleDayBeforeNotifications();

        ArgumentCaptor<ParticipantListRequestedPayload> captor =
                ArgumentCaptor.forClass(ParticipantListRequestedPayload.class);
        verify(eventTermEventPublisher).publishParticipantListRequested(captor.capture());
        ParticipantListRequestedPayload payload = captor.getValue();
        assertThat(payload.getEventTermId()).isEqualTo(termId);
        assertThat(payload.getCaregiverEmails()).containsExactly("caregiver1@example.test", "caregiver2@example.test");
        assertThat(payload.getEventName()).isEqualTo("Bike Adventure");
        assertThat(payload.getTermDate()).isEqualTo(start.toString());
    }

    @Test
    void caregiverNotFoundDoesNotPublish() {
        LocalDate tomorrow = LocalDate.now(fixedClock).plusDays(1);
        UUID termId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        EventTerm term = termWithCaregivers(termId, List.of(caregiverId), tomorrow.atTime(9, 0), "Bike Adventure");

        when(eventTermQueryService.findActiveTermsStartingOn(tomorrow)).thenReturn(List.of(term));
        when(identityServicePort.findCaregiverById(caregiverId)).thenReturn(Optional.empty());

        job.scheduleDayBeforeNotifications();

        verify(eventTermEventPublisher, never()).publishParticipantListRequested(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void noTermsTomorrowPublishesNothing() {
        LocalDate tomorrow = LocalDate.now(fixedClock).plusDays(1);
        when(eventTermQueryService.findActiveTermsStartingOn(tomorrow)).thenReturn(List.of());

        job.scheduleDayBeforeNotifications();

        verify(eventTermEventPublisher, never()).publishParticipantListRequested(org.mockito.ArgumentMatchers.any());
    }
}
