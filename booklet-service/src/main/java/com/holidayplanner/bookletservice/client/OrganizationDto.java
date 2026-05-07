package com.holidayplanner.bookletservice.client;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrganizationDto(
        UUID id,
        String name,
        String bankAccount,
        LocalDateTime bookingStartTime) {
}
