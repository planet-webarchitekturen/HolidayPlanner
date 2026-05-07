package com.holidayplanner.bookingservice.client;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class EventTermDetails {
    private UUID id;
    private UUID eventId;
    private int maxParticipants;
    private String status;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
}
