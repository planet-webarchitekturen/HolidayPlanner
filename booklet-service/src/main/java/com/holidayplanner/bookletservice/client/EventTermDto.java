package com.holidayplanner.bookletservice.client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EventTermDto(
        UUID id,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        int minParticipants,
        int maxParticipants,
        String status,
        List<UUID> caregiverIds) {
}
