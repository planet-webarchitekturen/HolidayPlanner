package com.holidayplanner.eventservice.dto;

import com.holidayplanner.shared.model.EventTerm;
import com.holidayplanner.shared.model.EventTermStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class EventTermResponse {
    private UUID id;
    private UUID eventId;
    private String eventName;
    private String eventLocation;
    private BigDecimal price;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private int minParticipants;
    private int maxParticipants;
    private EventTermStatus status;
    private UUID organizationId;

    public static EventTermResponse from(EventTerm term) {
        EventTermResponse r = new EventTermResponse();
        r.id = term.getId();
        r.eventId = term.getEvent().getId();
        r.eventName = term.getEvent().getShortTitle();
        r.eventLocation = term.getEvent().getLocation();
        r.price = term.getEvent().getPrice();
        r.startDateTime = term.getStartDateTime();
        r.endDateTime = term.getEndDateTime();
        r.minParticipants = term.getMinParticipants();
        r.maxParticipants = term.getMaxParticipants();
        r.status = term.getStatus();
        r.organizationId = term.getEvent().getOrganizationId();
        return r;
    }
}
