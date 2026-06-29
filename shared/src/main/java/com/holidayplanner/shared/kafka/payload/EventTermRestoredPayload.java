package com.holidayplanner.shared.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventTermRestoredPayload {
    private UUID eventTermId;
    private UUID organizationId;
}
