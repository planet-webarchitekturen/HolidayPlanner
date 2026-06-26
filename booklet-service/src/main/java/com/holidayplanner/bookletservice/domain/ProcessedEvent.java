package com.holidayplanner.bookletservice.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class ProcessedEvent {

  @Id private UUID eventId;

  protected ProcessedEvent() {}

  public ProcessedEvent(UUID eventId) {
    this.eventId = eventId;
  }
}
