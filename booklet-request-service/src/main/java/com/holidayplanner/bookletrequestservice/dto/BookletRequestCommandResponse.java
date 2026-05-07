package com.holidayplanner.bookletrequestservice.dto;

import com.holidayplanner.bookletrequestservice.model.BookletRequest;
import com.holidayplanner.bookletrequestservice.model.BookletRequestStatus;

import java.util.UUID;

public record BookletRequestCommandResponse(
        UUID id,
        UUID organizationId,
        int requestedCopies,
        BookletRequestStatus status,
        String note) {

    public static BookletRequestCommandResponse from(BookletRequest request) {
        return new BookletRequestCommandResponse(
                request.id(),
                request.organizationId(),
                request.requestedCopies(),
                request.status(),
                request.note());
    }
}
