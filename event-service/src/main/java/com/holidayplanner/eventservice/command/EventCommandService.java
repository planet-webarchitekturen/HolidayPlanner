package com.holidayplanner.eventservice.command;

import com.holidayplanner.eventservice.domain.exception.EventNotFoundException;
import com.holidayplanner.eventservice.dto.CreateEventRequest;
import com.holidayplanner.eventservice.dto.EventResponse;
import com.holidayplanner.eventservice.dto.UpdateEventRequest;
import com.holidayplanner.eventservice.repository.EventRepository;
import com.holidayplanner.shared.model.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EventCommandService {

    private final EventRepository eventRepository;

    public EventResponse createEvent(CreateEventRequest request) {
        Event event = new Event();
        event.setOrganizationId(request.getOrganizationId());
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
}
