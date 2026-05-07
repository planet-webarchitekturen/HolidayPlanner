package com.holidayplanner.eventservice.service;

import com.holidayplanner.eventservice.command.CancellationActor;
import com.holidayplanner.eventservice.command.EventTermCommandService;
import com.holidayplanner.eventservice.domain.exception.EventTermNotActiveException;
import com.holidayplanner.eventservice.domain.exception.InvalidStatusTransitionException;
import com.holidayplanner.eventservice.port.BookingServicePort;
import com.holidayplanner.eventservice.port.EventTermEventPublisher;
import com.holidayplanner.eventservice.port.NotificationPort;
import com.holidayplanner.eventservice.repository.EventRepository;
import com.holidayplanner.eventservice.repository.EventTermRepository;
import com.holidayplanner.shared.kafka.payload.CapacityIncreasedPayload;
import com.holidayplanner.shared.model.Event;
import com.holidayplanner.shared.model.EventTerm;
import com.holidayplanner.shared.model.EventTermStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventTermCommandServiceUnitTest {

    @Mock
    private EventTermRepository eventTermRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventTermEventPublisher eventTermEventPublisher;
    @Mock
    private BookingServicePort bookingServicePort;
    @Mock
    private NotificationPort notificationPort;

    @InjectMocks
    private EventTermCommandService commandService;

    private UUID termId;
    private Event event;
    private EventTerm term;

    @BeforeEach
    void setUp() {
        termId = UUID.randomUUID();
        event = new Event();
        event.setId(UUID.randomUUID());
        event.setShortTitle("Bike tour");
        event.setOrganizationId(UUID.randomUUID());
        term = new EventTerm();
        term.setId(termId);
        term.setEvent(event);
        term.setStartDateTime(LocalDateTime.now().plusDays(1));
        term.setEndDateTime(LocalDateTime.now().plusDays(1).plusHours(4));
        term.setMinParticipants(2);
        term.setMaxParticipants(10);
        term.setStatus(EventTermStatus.DRAFT);
    }

    @Test
    void changeStatus_draftToActive_succeeds() {
        when(eventTermRepository.findByIdWithEvent(termId)).thenReturn(Optional.of(term));
        when(eventTermRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        commandService.changeEventTermStatus(termId, EventTermStatus.ACTIVE, CancellationActor.EVENT_OWNER);

        verify(eventTermEventPublisher, never()).publishEventTermCancelled(any());
    }

    @Test
    void changeStatus_activeToDraft_throws() {
        term.setStatus(EventTermStatus.ACTIVE);
        when(eventTermRepository.findByIdWithEvent(termId)).thenReturn(Optional.of(term));

        assertThatThrownBy(() -> commandService.changeEventTermStatus(termId, EventTermStatus.DRAFT,
                CancellationActor.EVENT_OWNER))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void changeStatus_activeToCancelled_publishesKafka() {
        term.setStatus(EventTermStatus.ACTIVE);
        when(eventTermRepository.findByIdWithEvent(termId)).thenReturn(Optional.of(term));
        when(eventTermRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        commandService.changeEventTermStatus(termId, EventTermStatus.CANCELLED, CancellationActor.EVENT_OWNER);

        verify(eventTermEventPublisher).publishEventTermCancelled(argThat(p ->
                "EVENT_OWNER".equals(p.getCancelledBy())));
    }

    @Test
    void updateCapacity_whenMaxIncreased_publishesCapacityIncreased() {
        term.setStatus(EventTermStatus.ACTIVE);
        when(eventTermRepository.findByIdWithEvent(termId)).thenReturn(Optional.of(term));
        when(bookingServicePort.getConfirmedBookingCount(termId)).thenReturn(3L);
        when(eventTermRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        commandService.updateEventTermCapacity(termId, 2, 15);

        ArgumentCaptor<CapacityIncreasedPayload> cap = ArgumentCaptor.forClass(CapacityIncreasedPayload.class);
        verify(eventTermEventPublisher).publishCapacityIncreased(cap.capture());
        assertThat(cap.getValue().getAddedSlots()).isEqualTo(5);
        assertThat(cap.getValue().getNewMax()).isEqualTo(15);
    }

    @Test
    void sendMessage_whenNotActive_throws() {
        term.setStatus(EventTermStatus.DRAFT);
        when(eventTermRepository.findByIdWithEvent(termId)).thenReturn(Optional.of(term));

        assertThatThrownBy(() -> commandService.sendMessageToParticipants(termId, "Hi", "Body"))
                .isInstanceOf(EventTermNotActiveException.class);
    }

    @Test
    void sendMessage_whenActive_callsNotification() {
        term.setStatus(EventTermStatus.ACTIVE);
        when(eventTermRepository.findByIdWithEvent(termId)).thenReturn(Optional.of(term));
        when(bookingServicePort.getParticipantParentEmails(termId)).thenReturn(List.of("p@example.com"));

        commandService.sendMessageToParticipants(termId, "Subject", "Hello");

        verify(notificationPort).sendBulkEmail(List.of("p@example.com"), "Subject", "Hello");
    }
}
