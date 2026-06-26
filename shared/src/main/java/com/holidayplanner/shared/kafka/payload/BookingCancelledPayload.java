package com.holidayplanner.shared.kafka.payload;

import com.holidayplanner.shared.model.CancelledBy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelledPayload {
    private UUID bookingId;
    private UUID familyMemberId;
    private UUID eventTermId;
    private String parentEmail;
    private String eventName;
    private String termDate;
    private CancelledBy cancelledBy;
}
