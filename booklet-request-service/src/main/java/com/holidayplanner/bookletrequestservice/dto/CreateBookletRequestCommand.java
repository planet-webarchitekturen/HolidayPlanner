package com.holidayplanner.bookletrequestservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBookletRequestCommand(
        @NotNull UUID organizationId,
        @Min(1) int requestedCopies,
        String note) {
}
