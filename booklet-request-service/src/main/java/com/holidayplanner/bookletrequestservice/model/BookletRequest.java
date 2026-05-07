package com.holidayplanner.bookletrequestservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookletRequest(
        UUID id,
        UUID organizationId,
        int requestedCopies,
        BookletRequestStatus status,
        String note,
        LocalDateTime createdAt,
        LocalDateTime lastUpdatedAt) {

    public BookletRequest withStatus(BookletRequestStatus newStatus, LocalDateTime updatedAt) {
        return new BookletRequest(id, organizationId, requestedCopies, newStatus, note, createdAt, updatedAt);
    }
}
