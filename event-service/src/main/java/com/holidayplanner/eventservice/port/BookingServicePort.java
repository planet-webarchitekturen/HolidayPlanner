package com.holidayplanner.eventservice.port;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for booking-service (confirmed counts, participant contact data).
 */
public interface BookingServicePort {

    long getConfirmedBookingCount(UUID eventTermId);

    /**
     * Parent emails for confirmed participants. Returns empty list if endpoint is missing or fails.
     */
    List<String> getParticipantParentEmails(UUID eventTermId);

    /**
     * Display names for confirmed participants (for caregiver PDF / Kafka payload).
     */
    List<String> getParticipantDisplayNames(UUID eventTermId);
}
