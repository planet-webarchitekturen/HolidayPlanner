package com.holidayplanner.bookletservice.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EventDto(
        UUID id,
        UUID organizationId,
        UUID eventOwnerId,
        String shortTitle,
        String description,
        String pictureUrl,
        String location,
        String meetingPoint,
        BigDecimal price,
        String paymentMethod,
        int minimalAge,
        int maximalAge,
        List<EventTermDto> terms) {
}
