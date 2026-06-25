package com.holidayplanner.bookingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActiveBookingCheckResponse {
    private boolean hasActiveBookings;
    private long activeBookingCount;
}
