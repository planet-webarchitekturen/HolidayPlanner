package com.holidayplanner.bookletrequestservice.dto;

import java.util.UUID;

public record BookletRequestSummaryResponse(
        UUID organizationId,
        long requestedCount,
        long printedCount,
        long distributedCount,
        long cancelledCount,
        int totalRequestedCopies) {
}
