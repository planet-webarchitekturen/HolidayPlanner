package com.holidayplanner.bookletservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.ParticipantListPdfGeneratedPayload;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookletEventProducer {

  public static final String TOPIC_PARTICIPANT_LIST_PDF =
      "holiday-planner.booklet.participant-list-pdf-generated";

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public void publishParticipantListPdfGenerated(ParticipantListPdfGeneratedPayload payload) {
    try {
      KafkaEnvelope<ParticipantListPdfGeneratedPayload> envelope =
          new KafkaEnvelope<>(
              "ParticipantListPdfGenerated",
              "1",
              LocalDateTime.now().toString(),
              "booklet-service",
              payload);
      kafkaTemplate.send(
          TOPIC_PARTICIPANT_LIST_PDF,
          payload.getEventTermId().toString(),
          objectMapper.writeValueAsString(envelope));
    } catch (Exception e) {
      log.error("Failed to publish ParticipantListPdfGenerated event", e);
    }
  }
}
