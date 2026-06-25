package com.holidayplanner.eventservice.command;

import com.holidayplanner.eventservice.domain.EventTermStatusTransitions;
import com.holidayplanner.eventservice.domain.exception.EventNotFoundException;
import com.holidayplanner.eventservice.domain.exception.EventTermNotActiveException;
import com.holidayplanner.eventservice.domain.exception.EventTermNotFoundException;
import com.holidayplanner.eventservice.domain.exception.InvalidCapacityException;
import com.holidayplanner.eventservice.dto.CreateEventTermRequest;
import com.holidayplanner.eventservice.dto.EventTermResponse;
import com.holidayplanner.eventservice.port.BookingServicePort;
import com.holidayplanner.eventservice.port.EventTermEventPublisher;
import com.holidayplanner.eventservice.repository.EventRepository;
import com.holidayplanner.eventservice.repository.EventTermRepository;
import com.holidayplanner.eventservice.saga.EventTermCancellationSaga;
import com.holidayplanner.shared.kafka.payload.CapacityIncreasedPayload;
import com.holidayplanner.shared.kafka.payload.ParticipantMessageRequestedPayload;
import com.holidayplanner.shared.model.Event;
import com.holidayplanner.shared.model.EventTerm;
import com.holidayplanner.shared.model.EventTermStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventTermCommandService {

    private final EventTermRepository eventTermRepository;
    private final EventRepository eventRepository;
    private final EventTermEventPublisher eventTermEventPublisher;
    private final BookingServicePort bookingServicePort;
    private final EventTermCancellationSaga eventTermCancellationSaga;

    public EventTermResponse createEventTerm(UUID eventId, CreateEventTermRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        EventTerm term = new EventTerm();
        term.setEvent(event);
        term.setStartDateTime(request.getStartDateTime());
        term.setEndDateTime(request.getEndDateTime());
        term.setMinParticipants(request.getMinParticipants());
        term.setMaxParticipants(request.getMaxParticipants());
        term.setStatus(EventTermStatus.DRAFT);
        EventTerm saved = eventTermRepository.save(term);
        return EventTermResponse.from(saved);
    }

    public EventTermResponse changeEventTermStatus(UUID eventTermId, EventTermStatus newStatus, CancellationActor actor) {
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus is required");
        }
        EventTerm term = eventTermRepository.findByIdWithEvent(eventTermId)
                .orElseThrow(() -> new EventTermNotFoundException(eventTermId));
        EventTermStatus current = term.getStatus();
        EventTermStatusTransitions.requireTransition(current, newStatus);
        term.setStatus(newStatus);
        EventTerm saved = eventTermRepository.save(term);

        if (newStatus == EventTermStatus.CANCELLED) {
            eventTermCancellationSaga.start(term, actor);
        }
        return EventTermResponse.from(saved);
    }

    public EventTermResponse updateEventTermCapacity(UUID eventTermId, int minParticipants, int maxParticipants) {
        EventTerm term = eventTermRepository.findByIdWithEvent(eventTermId)
                .orElseThrow(() -> new EventTermNotFoundException(eventTermId));
        if (maxParticipants < minParticipants) {
            throw new InvalidCapacityException("maxParticipants must be >= minParticipants");
        }
        long confirmed = bookingServicePort.getConfirmedBookingCount(eventTermId);
        if (maxParticipants < confirmed) {
            throw new InvalidCapacityException(
                    "maxParticipants (" + maxParticipants + ") must be >= confirmed bookings (" + confirmed + ")");
        }
        int oldMax = term.getMaxParticipants();
        term.setMinParticipants(minParticipants);
        term.setMaxParticipants(maxParticipants);
        EventTerm saved = eventTermRepository.save(term);
        if (maxParticipants > oldMax) {
            int addedSlots = maxParticipants - oldMax;
            eventTermEventPublisher.publishCapacityIncreased(
                    new CapacityIncreasedPayload(eventTermId, addedSlots, maxParticipants));
        }
        return EventTermResponse.from(saved);
    }

    public EventTermResponse assignCaregiverToEventTerm(UUID eventTermId, UUID caregiverId) {
        EventTerm term = eventTermRepository.findByIdWithEvent(eventTermId)
                .orElseThrow(() -> new EventTermNotFoundException(eventTermId));
        if (!term.getCaregiverIds().contains(caregiverId)) {
            term.getCaregiverIds().add(caregiverId);
        }
        return EventTermResponse.from(eventTermRepository.save(term));
    }

    public void sendMessageToParticipants(UUID eventTermId, String subject, String messageBody) {
        EventTerm term = eventTermRepository.findByIdWithEvent(eventTermId)
                .orElseThrow(() -> new EventTermNotFoundException(eventTermId));
        if (term.getStatus() != EventTermStatus.ACTIVE) {
            throw new EventTermNotActiveException(eventTermId);
        }
        List<String> emails = bookingServicePort.getParticipantParentEmails(eventTermId);
        if (emails.isEmpty()) {
            log.warn("No participant parent emails returned for event term {} — bulk email skipped", eventTermId);
            return;
        }
        String resolvedSubject = subject != null && !subject.isBlank()
                ? subject
                : "Message regarding: " + (term.getEvent() != null ? term.getEvent().getShortTitle() : "your event");
        eventTermEventPublisher.publishParticipantMessageRequested(
                new ParticipantMessageRequestedPayload(eventTermId, emails, resolvedSubject, messageBody));
    }
}
