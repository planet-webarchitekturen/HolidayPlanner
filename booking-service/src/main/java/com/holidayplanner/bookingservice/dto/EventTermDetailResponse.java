package com.holidayplanner.bookingservice.dto;

import com.holidayplanner.shared.model.PaymentMethod;
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
    private String meetingPoint;
    private BigDecimal price;
    private PaymentMethod paymentMethod;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private int maxParticipants;
    private Integer minimalAge;
    private Integer maximalAge;
    private String status;
    private UUID organizationId;
}
