package com.holidayplanner.shared.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserOrganizationUpdatedPayload {
    private UUID userId;
    private UUID organizationId; // Can be null if organization is deleted
}
