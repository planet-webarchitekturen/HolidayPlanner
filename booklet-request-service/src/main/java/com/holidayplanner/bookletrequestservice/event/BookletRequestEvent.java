package com.holidayplanner.bookletrequestservice.event;

import com.holidayplanner.bookletrequestservice.model.BookletRequestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookletRequestEvent(
        UUID eventId,
        BookletRequestEventType type,
        UUID requestId,
        UUID organizationId,
        int requestedCopies,
        BookletRequestStatus status,
        String note,
        LocalDateTime occurredAt) {
}
