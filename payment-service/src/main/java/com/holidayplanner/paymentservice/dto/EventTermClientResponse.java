package com.holidayplanner.paymentservice.dto;

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
public class EventTermClientResponse {
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
}
