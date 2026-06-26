package com.holidayplanner.shared.kafka.payload;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantListRequestedPayload {
  private UUID eventTermId;
  private List<String> caregiverEmails;
  private String eventName;
  private String termDate;
}
