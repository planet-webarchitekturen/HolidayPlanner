package com.holidayplanner.bookingservice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Subset of organization-service's OrganizationResponse needed by booking-service. */
@Getter
@Setter
@NoArgsConstructor
public class OrganizationDto {
    private UUID id;
    private String name;
    private LocalDateTime bookingStartTime;
}
