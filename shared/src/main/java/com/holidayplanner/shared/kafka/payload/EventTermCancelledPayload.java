package com.holidayplanner.shared.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventTermCancelledPayload {
    private UUID eventTermId;
    private String eventName;
    private String termDate;
    private UUID organizationId;
    private List<String> caregiverEmails;
    private String cancelledBy;
}
