package com.holidayplanner.paymentservice.dto;

import com.holidayplanner.shared.model.BookingStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class BookingClientResponse {
    private UUID id;
    private UUID familyMemberId;
    private UUID eventTermId;
    private BookingStatus status;
    private LocalDateTime bookedAt;
}
