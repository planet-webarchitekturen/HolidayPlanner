package com.holidayplanner.bookletservice.client;

import java.math.BigDecimal;
import java.util.UUID;

public record SponsorDto(
        UUID id,
        UUID organizationId,
        String name,
        BigDecimal amount) {
}
