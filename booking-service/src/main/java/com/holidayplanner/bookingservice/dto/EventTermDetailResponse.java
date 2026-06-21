package com.holidayplanner.bookingservice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class EventTermDetailResponse {
    private UUID id;
    private UUID eventId;
    private String eventName;
    private String eventLocation;
    private BigDecimal price;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private int maxParticipants;
    private String status;
    private UUID organizationId;
}
