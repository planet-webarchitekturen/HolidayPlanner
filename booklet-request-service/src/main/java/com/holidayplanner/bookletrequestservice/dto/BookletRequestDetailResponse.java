package com.holidayplanner.bookletrequestservice.dto;

import com.holidayplanner.bookletrequestservice.model.BookletRequestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookletRequestDetailResponse(
        UUID id,
        UUID organizationId,
        int requestedCopies,
        BookletRequestStatus status,
        String note,
        LocalDateTime createdAt,
        LocalDateTime lastUpdatedAt) {
}
