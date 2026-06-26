package com.holidayplanner.notificationservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.notificationservice.service.NotificationService;
import com.holidayplanner.notificationservice.service.ProcessedEventService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.ParticipantListPdfGeneratedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantListPdfGeneratedConsumer {

  private final NotificationService notificationService;
  private final ProcessedEventService processedEventService;
  private final ObjectMapper objectMapper;

  @KafkaListener(
      topics = "holiday-planner.booklet.participant-list-pdf-generated",
      groupId = "notification-service")
  public void consume(String message) throws Exception {
    try {
      KafkaEnvelope<ParticipantListPdfGeneratedPayload> envelope =
          objectMapper.readValue(
              message, new TypeReference<KafkaEnvelope<ParticipantListPdfGeneratedPayload>>() {});
      ParticipantListPdfGeneratedPayload payload = envelope.getPayload();
      processedEventService.process(
          envelope.getEventId(),
          () ->
              notificationService.notifyCaregiverWithParticipantListPdf(
                  payload.getEventTermId(),
                  payload.getCaregiverEmails(),
                  payload.getEventName(),
                  payload.getTermDate()));
    } catch (Exception e) {
      log.error("Failed to process ParticipantListPdfGenerated event: {}", e.getMessage());
      throw e;
    }
  }
}
