package com.holidayplanner.paymentservice.dto;

import com.holidayplanner.shared.model.BookingStatus;
import com.holidayplanner.shared.model.PaymentStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class EventTermPaymentParticipantResponse {
    private UUID bookingId;
    private UUID familyMemberId;
    private BookingStatus bookingStatus;
    private LocalDateTime bookedAt;

    private UUID paymentId;
    private BigDecimal amount;
    private PaymentStatus paymentStatus;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private String note;
    private boolean paymentAvailable;
}
