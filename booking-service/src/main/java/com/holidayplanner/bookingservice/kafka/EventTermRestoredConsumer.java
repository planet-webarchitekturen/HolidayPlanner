package com.holidayplanner.bookingservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.bookingservice.command.BookingCommandService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.EventTermRestoredPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventTermRestoredConsumer {

    private final BookingCommandService bookingCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "holiday-planner.event.term-restored",
            groupId = "booking-service-term-restored"
    )
    public void consume(String message) {
        try {
            KafkaEnvelope<EventTermRestoredPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<EventTermRestoredPayload>>() {});
            EventTermRestoredPayload payload = envelope.getPayload();
            bookingCommandService.restoreAllBookingsForTerm(
                    payload.getEventTermId(), payload.getOrganizationId());
            log.info("Restored all saga-cancelled bookings for event term {}", payload.getEventTermId());
        } catch (Exception e) {
            log.error("Failed to process EventTermRestored event: {}", e.getMessage(), e);
        }
    }
}
