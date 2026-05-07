package com.holidayplanner.eventservice.query;

import com.holidayplanner.eventservice.domain.exception.EventNotFoundException;
import com.holidayplanner.eventservice.dto.EventResponse;
import com.holidayplanner.eventservice.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventQueryService {

    private final EventRepository eventRepository;

    public List<EventResponse> getEventsByOrganization(UUID organizationId) {
        return eventRepository.findByOrganizationId(organizationId).stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
    }

    public EventResponse getEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .map(EventResponse::from)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }
}
