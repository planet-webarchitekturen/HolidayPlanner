package com.holidayplanner.paymentservice.dto;

import com.holidayplanner.shared.model.EventTermStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class EventTermPaymentOverviewResponse {
    private UUID eventTermId;
    private UUID eventId;
    private String eventName;
    private String eventLocation;
    private BigDecimal price;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private int minParticipants;
    private int maxParticipants;
    private EventTermStatus eventTermStatus;

    private long bookingCount;
    private long billableBookingCount;
    private long paidCount;
    private long pendingCount;
    private long refundedCount;
    private long missingPaymentCount;

    private BigDecimal totalExpectedAmount = BigDecimal.ZERO;
    private BigDecimal totalPaidAmount = BigDecimal.ZERO;
    private BigDecimal totalPendingAmount = BigDecimal.ZERO;
    private BigDecimal totalRefundedAmount = BigDecimal.ZERO;
    private BigDecimal totalOpenAmount = BigDecimal.ZERO;

    private List<EventTermPaymentParticipantResponse> participants = new ArrayList<>();
}
