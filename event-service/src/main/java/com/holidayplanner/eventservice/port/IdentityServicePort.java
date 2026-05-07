package com.holidayplanner.eventservice.port;

import com.holidayplanner.shared.model.Caregiver;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for identity-service (caregiver resolution).
 */
public interface IdentityServicePort {

    Optional<Caregiver> findCaregiverById(UUID caregiverId);
}
