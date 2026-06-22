package com.holidayplanner.bookingservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.bookingservice.command.BookingCommandService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.CapacityIncreasedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CapacityIncreasedConsumer {

    private final BookingCommandService bookingCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "holiday-planner.event.capacity-increased", groupId = "booking-service")
    public void consume(String message) {
        try {
            KafkaEnvelope<CapacityIncreasedPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<CapacityIncreasedPayload>>() {});
            CapacityIncreasedPayload payload = envelope.getPayload();
            bookingCommandService.promoteFromWaitingList(payload.getEventTermId(), payload.getAddedSlots());
            log.info("Promoted up to {} waitlisted bookings for event term {} after capacity increase",
                    payload.getAddedSlots(), payload.getEventTermId());
        } catch (Exception e) {
            log.error("Failed to process CapacityIncreased event: {}", e.getMessage());
        }
    }
}
