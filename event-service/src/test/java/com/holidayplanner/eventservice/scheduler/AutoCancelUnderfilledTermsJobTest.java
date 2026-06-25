package com.holidayplanner.eventservice.scheduler;

import com.holidayplanner.eventservice.command.CancellationActor;
import com.holidayplanner.eventservice.command.EventTermCommandService;
import com.holidayplanner.eventservice.port.BookingServicePort;
import com.holidayplanner.eventservice.query.EventTermQueryService;
import com.holidayplanner.shared.model.EventTerm;
import com.holidayplanner.shared.model.EventTermStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoCancelUnderfilledTermsJobTest {

    private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");

    @Mock
    private EventTermQueryService eventTermQueryService;
    @Mock
    private EventTermCommandService eventTermCommandService;
    @Mock
    private BookingServicePort bookingServicePort;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-22T01:00:00Z"), VIENNA);

    private AutoCancelUnderfilledTermsJob job;

    @BeforeEach
    void setUp() {
        job = new AutoCancelUnderfilledTermsJob(
                eventTermQueryService, eventTermCommandService, bookingServicePort, fixedClock);
    }

    private EventTerm term(UUID id, int minParticipants) {
        EventTerm t = new EventTerm();
        t.setId(id);
        t.setMinParticipants(minParticipants);
        t.setStatus(EventTermStatus.ACTIVE);
        return t;
    }

    @Test
    void underfilledTermIsCancelledBySystem() {
        UUID id = UUID.randomUUID();
        when(eventTermQueryService.findActiveTermsStartingWithin24Hours(LocalDateTime.now(fixedClock)))
                .thenReturn(List.of(term(id, 5)));
        when(bookingServicePort.getConfirmedBookingCount(id)).thenReturn(1L);

        job.autoCancelUnderfilledTerms();

        verify(eventTermCommandService)
                .changeEventTermStatus(id, EventTermStatus.CANCELLED, CancellationActor.SYSTEM);
    }

    @Test
    void sufficientlyFilledTermIsNotCancelled() {
        UUID id = UUID.randomUUID();
        when(eventTermQueryService.findActiveTermsStartingWithin24Hours(LocalDateTime.now(fixedClock)))
                .thenReturn(List.of(term(id, 2)));
        when(bookingServicePort.getConfirmedBookingCount(id)).thenReturn(3L);

        job.autoCancelUnderfilledTerms();

        verify(eventTermCommandService, never())
                .changeEventTermStatus(eq(id), eq(EventTermStatus.CANCELLED), eq(CancellationActor.SYSTEM));
    }

    @Test
    void exactlyAtMinimumIsNotCancelled() {
        UUID id = UUID.randomUUID();
        when(eventTermQueryService.findActiveTermsStartingWithin24Hours(LocalDateTime.now(fixedClock)))
                .thenReturn(List.of(term(id, 4)));
        when(bookingServicePort.getConfirmedBookingCount(id)).thenReturn(4L);

        job.autoCancelUnderfilledTerms();

        verify(eventTermCommandService, never())
                .changeEventTermStatus(eq(id), eq(EventTermStatus.CANCELLED), eq(CancellationActor.SYSTEM));
    }

    @Test
    void oneTermFailingDoesNotStopProcessingOthers() {
        UUID failing = UUID.randomUUID();
        UUID underfilled = UUID.randomUUID();
        when(eventTermQueryService.findActiveTermsStartingWithin24Hours(LocalDateTime.now(fixedClock)))
                .thenReturn(List.of(term(failing, 5), term(underfilled, 5)));
        when(bookingServicePort.getConfirmedBookingCount(failing))
                .thenThrow(new RuntimeException("booking-service down"));
        when(bookingServicePort.getConfirmedBookingCount(underfilled)).thenReturn(0L);

        job.autoCancelUnderfilledTerms();

        verify(eventTermCommandService, never())
                .changeEventTermStatus(eq(failing), eq(EventTermStatus.CANCELLED), eq(CancellationActor.SYSTEM));
        verify(eventTermCommandService)
                .changeEventTermStatus(underfilled, EventTermStatus.CANCELLED, CancellationActor.SYSTEM);
    }
}
