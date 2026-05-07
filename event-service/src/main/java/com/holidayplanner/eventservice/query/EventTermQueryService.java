package com.holidayplanner.eventservice.query;

import com.holidayplanner.eventservice.domain.exception.EventTermNotFoundException;
import com.holidayplanner.eventservice.dto.EventTermResponse;
import com.holidayplanner.eventservice.repository.EventTermRepository;
import com.holidayplanner.shared.model.EventTerm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventTermQueryService {

    private final EventTermRepository eventTermRepository;

    public EventTermResponse getEventTerm(UUID eventTermId) {
        EventTerm term = eventTermRepository.findByIdWithEvent(eventTermId)
                .orElseThrow(() -> new EventTermNotFoundException(eventTermId));
        return EventTermResponse.from(term);
    }

    public List<EventTerm> findActiveTermsStartingWithin24Hours(LocalDateTime now) {
        return eventTermRepository.findActiveTermsStartingInWindow(now, now.plusHours(24));
    }

    public List<EventTerm> findActiveTermsStartingOn(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        return eventTermRepository.findActiveTermsStartingBetween(start, end);
    }
}
