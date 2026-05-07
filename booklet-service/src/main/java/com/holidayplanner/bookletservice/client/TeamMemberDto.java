package com.holidayplanner.bookletservice.client;

import java.util.UUID;

public record TeamMemberDto(
        UUID id,
        UUID organizationId,
        UUID userId,
        String firstName,
        String lastName,
        String email,
        String role) {
}
