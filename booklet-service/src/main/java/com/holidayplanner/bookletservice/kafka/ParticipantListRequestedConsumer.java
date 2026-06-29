package com.holidayplanner.bookletservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.bookletservice.service.BookletService;
import com.holidayplanner.bookletservice.service.ProcessedEventService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.ParticipantListRequestedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantListRequestedConsumer {

  private final BookletService bookletService;
  private final ProcessedEventService processedEventService;
  private final ObjectMapper objectMapper;

  @KafkaListener(
      topics = "holiday-planner.event.participant-list-requested",
      groupId = "booklet-service")
  public void consume(String message) throws Exception {
    try {
      KafkaEnvelope<ParticipantListRequestedPayload> envelope =
          objectMapper.readValue(
              message, new TypeReference<KafkaEnvelope<ParticipantListRequestedPayload>>() {});
      ParticipantListRequestedPayload payload = envelope.getPayload();
      processedEventService.process(
          envelope.getEventId(),
          () ->
              bookletService.createParticipantListPdf(
                  payload.getEventTermId(),
                  payload.getCaregiverEmails(),
                  payload.getEventName(),
                  payload.getTermDate()));
    } catch (Exception e) {
      log.error("Failed to process ParticipantListRequested event: {}", e.getMessage());
      throw e;
    }
  }
}
