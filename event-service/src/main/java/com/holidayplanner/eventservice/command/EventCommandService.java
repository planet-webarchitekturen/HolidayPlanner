package com.holidayplanner.eventservice.command;

import com.holidayplanner.eventservice.domain.exception.EventNotFoundException;
import com.holidayplanner.eventservice.dto.CreateEventRequest;
import com.holidayplanner.eventservice.dto.EventResponse;
import com.holidayplanner.eventservice.dto.UpdateEventRequest;
import com.holidayplanner.eventservice.repository.EventRepository;
import com.holidayplanner.eventservice.repository.EventTermRepository;
import com.holidayplanner.eventservice.saga.EventTermCancellationSaga;
import com.holidayplanner.shared.model.Event;
import com.holidayplanner.shared.model.EventTerm;
import com.holidayplanner.shared.model.EventTermStatus;
import com.holidayplanner.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EventCommandService {

    private final EventRepository eventRepository;
    private final EventTermRepository eventTermRepository;
    private final EventTermCancellationSaga eventTermCancellationSaga;

    public EventResponse createEvent(CreateEventRequest request) {
        Event event = new Event();
        UUID jwtOrgId = SecurityUtils.getCurrentOrganizationId();
        event.setOrganizationId(jwtOrgId != null ? jwtOrgId : request.getOrganizationId());
        event.setEventOwnerId(request.getEventOwnerId());
        event.setShortTitle(request.getShortTitle());
        event.setDescription(request.getDescription());
        event.setLocation(request.getLocation());
        event.setMeetingPoint(request.getMeetingPoint());
        event.setPrice(request.getPrice());
        event.setPaymentMethod(request.getPaymentMethod());
        event.setMinimalAge(request.getMinimalAge());
        event.setMaximalAge(request.getMaximalAge());
        event.setPictureUrl(request.getPictureUrl());
        Event saved = eventRepository.save(event);
        return EventResponse.from(saved);
    }

    public EventResponse updateEvent(UUID eventId, UpdateEventRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        UUID currentOrgId = SecurityUtils.getCurrentOrganizationId();
        if (currentOrgId != null && event.getOrganizationId() != null
                && !currentOrgId.equals(event.getOrganizationId())) {
            throw new AccessDeniedException("Event belongs to a different organization");
        }

        event.setShortTitle(request.getShortTitle());
        event.setDescription(request.getDescription());
        event.setLocation(request.getLocation());
        event.setMeetingPoint(request.getMeetingPoint());
        event.setPrice(request.getPrice());
        event.setPaymentMethod(request.getPaymentMethod());
        event.setMinimalAge(request.getMinimalAge());
        event.setMaximalAge(request.getMaximalAge());
        event.setPictureUrl(request.getPictureUrl());
        return EventResponse.from(eventRepository.save(event));
    }

    public void deleteEventsByOrganization(UUID organizationId) {
        List<Event> events = eventRepository.findByOrganizationId(organizationId);
        // Cancel each ACTIVE term first so the existing EventTermCancelled choreography cascades:
        // booking-service cancels its bookings, payment-service refunds them, and parents/caregivers
        // are notified — leaving no orphaned data once the events are deleted.
        for (Event event : events) {
            for (EventTerm term : eventTermRepository.findByEvent_Id(event.getId())) {
                if (term.getStatus() == EventTermStatus.ACTIVE) {
                    eventTermCancellationSaga.start(term, CancellationActor.SYSTEM);
                }
            }
        }
        eventRepository.deleteAll(events);
    }
}
