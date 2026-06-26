package com.holidayplanner.bookletservice.service;

import com.holidayplanner.bookletservice.domain.ProcessedEvent;
import com.holidayplanner.bookletservice.repository.ProcessedEventRepository;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessedEventService {

  private final ProcessedEventRepository repository;

  public void process(UUID eventId, ThrowingRunnable action) throws Exception {
    Objects.requireNonNull(eventId, "eventId must not be null");

    if (repository.existsById(eventId)) {
      log.info("Skipping already processed event {}", eventId);
      return;
    }

    action.run();
    repository.saveAndFlush(new ProcessedEvent(eventId));
  }

  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Exception;
  }
}
