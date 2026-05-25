package com.holidayplanner.eventservice.saga;

import com.holidayplanner.eventservice.command.CancellationActor;
import com.holidayplanner.eventservice.port.EventTermEventPublisher;
import com.holidayplanner.eventservice.repository.CaregiverRepository;
import com.holidayplanner.shared.kafka.payload.EventTermCancelledPayload;
import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.Event;
import com.holidayplanner.shared.model.EventTerm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventTermCancellationSagaTest {

    @Mock
    private CaregiverRepository caregiverRepository;
    @Mock
    private EventTermEventPublisher eventTermEventPublisher;

    @InjectMocks
    private EventTermCancellationSaga saga;

    @Test
    void start_publishesCancellationEventWithContext() {
        UUID caregiverId = UUID.randomUUID();
        Event event = new Event();
        event.setShortTitle("Bike tour");
        event.setOrganizationId(UUID.randomUUID());
        EventTerm term = new EventTerm();
        term.setId(UUID.randomUUID());
        term.setEvent(event);
        term.setStartDateTime(LocalDateTime.of(2026, 6, 1, 9, 0));
        term.getCaregiverIds().add(caregiverId);
        Caregiver caregiver = new Caregiver();
        caregiver.setEmail("caregiver@example.com");
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));

        saga.start(term, CancellationActor.EVENT_OWNER);

        ArgumentCaptor<EventTermCancelledPayload> captor =
                ArgumentCaptor.forClass(EventTermCancelledPayload.class);
        verify(eventTermEventPublisher).publishEventTermCancelled(captor.capture());
        EventTermCancelledPayload payload = captor.getValue();
        assertThat(payload.getEventTermId()).isEqualTo(term.getId());
        assertThat(payload.getEventName()).isEqualTo("Bike tour");
        assertThat(payload.getTermDate()).isEqualTo("2026-06-01T09:00");
        assertThat(payload.getOrganizationId()).isEqualTo(event.getOrganizationId());
        assertThat(payload.getCaregiverEmails()).containsExactly("caregiver@example.com");
        assertThat(payload.getCancelledBy()).isEqualTo("EVENT_OWNER");
    }
}
