package com.holidayplanner.shared.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationCreatedPayload {
    private UUID organizationId;
    private String name;
    private String bankAccount;
    private String bookingStartTime;
}
