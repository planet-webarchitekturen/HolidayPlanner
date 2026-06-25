package com.holidayplanner.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.holidayplanner.notificationservice.domain.ProcessedEvent;
import com.holidayplanner.notificationservice.repository.ProcessedEventRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProcessedEventServiceTest {

  @Test
  void rejectsMissingEventId() {
    ProcessedEventService service = new ProcessedEventService(mock(ProcessedEventRepository.class));

    assertThatThrownBy(() -> service.process(null, () -> {}))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("eventId must not be null");
  }

  @Test
  void skipsDuplicateEventId() throws Exception {
    ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    UUID eventId = UUID.randomUUID();
    when(repository.existsById(eventId)).thenReturn(false, true);
    ProcessedEventService service = new ProcessedEventService(repository);
    AtomicInteger calls = new AtomicInteger();

    service.process(eventId, calls::incrementAndGet);
    service.process(eventId, calls::incrementAndGet);

    assertThat(calls).hasValue(1);
    verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.any(ProcessedEvent.class));
  }

  @Test
  void marksEventAfterAction() throws Exception {
    ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    UUID eventId = UUID.randomUUID();
    ProcessedEventService service = new ProcessedEventService(repository);
    AtomicInteger calls = new AtomicInteger();

    service.process(
        eventId,
        () -> {
          verify(repository, org.mockito.Mockito.never())
              .saveAndFlush(org.mockito.ArgumentMatchers.any(ProcessedEvent.class));
          calls.incrementAndGet();
        });

    assertThat(calls).hasValue(1);
    verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.any(ProcessedEvent.class));
  }
}
