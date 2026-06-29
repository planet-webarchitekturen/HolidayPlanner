package com.holidayplanner.shared.kafka;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KafkaEnvelope<T> {
  private String eventType;
  private UUID eventId;
  private String version;
  private String timestamp;
  private String source;
  private T payload;

  public KafkaEnvelope(
      String eventType, String version, String timestamp, String source, T payload) {
    this(eventType, UUID.randomUUID(), version, timestamp, source, payload);
  }
}
