package com.holidayplanner.shared.kafka.payload;

import com.holidayplanner.shared.model.PaymentMethod;
import com.holidayplanner.shared.model.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreatedPayload {
    private UUID bookingId;
    private UUID familyMemberId;
    private UUID eventTermId;
    private BookingStatus status;
    private String parentEmail;
    private String eventName;
    private String termDate;
    private String meetingPoint;
    private PaymentMethod paymentMethod;
    private UUID organizationId;
    private BigDecimal amount;
}
