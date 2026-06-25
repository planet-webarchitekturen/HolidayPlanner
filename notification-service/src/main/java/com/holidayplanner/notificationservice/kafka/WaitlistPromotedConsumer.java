package com.holidayplanner.notificationservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.notificationservice.service.NotificationService;
import com.holidayplanner.notificationservice.service.ProcessedEventService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.WaitlistPromotedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistPromotedConsumer {

  private final NotificationService notificationService;
  private final ProcessedEventService processedEventService;
  private final ObjectMapper objectMapper;

  @KafkaListener(
      topics = "holiday-planner.booking.waitlist-promoted",
      groupId = "notification-service")
  public void consume(String message) throws Exception {
    try {
      KafkaEnvelope<WaitlistPromotedPayload> envelope =
          objectMapper.readValue(
              message, new TypeReference<KafkaEnvelope<WaitlistPromotedPayload>>() {});
      WaitlistPromotedPayload payload = envelope.getPayload();
      processedEventService.process(
          envelope.getEventId(),
          () ->
              notificationService.notifyWaitlistPromoted(
                  payload.getParentEmail(), payload.getEventName(), payload.getTermDate()));
    } catch (Exception e) {
      log.error("Failed to process WaitlistPromoted event: {}", e.getMessage());
      throw e;
    }
  }
}
