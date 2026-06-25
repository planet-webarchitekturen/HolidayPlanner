package com.holidayplanner.bookingservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import com.holidayplanner.shared.kafka.payload.BookingRestoredPayload;
import com.holidayplanner.shared.kafka.payload.WaitlistPromotedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishBookingCreated(BookingCreatedPayload payload) {
        try {
            KafkaEnvelope<BookingCreatedPayload> envelope = new KafkaEnvelope<>(
                    "BookingCreated", "1",
                    LocalDateTime.now().toString(),
                    "booking-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.booking.created",
                    payload.getBookingId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish BookingCreated event", e);
        }
    }

    public void publishBookingCancelled(BookingCancelledPayload payload) {
        try {
            KafkaEnvelope<BookingCancelledPayload> envelope = new KafkaEnvelope<>(
                    "BookingCancelled", "1",
                    LocalDateTime.now().toString(),
                    "booking-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.booking.cancelled",
                    payload.getBookingId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish BookingCancelled event", e);
        }
    }

    public void publishBookingRestored(BookingRestoredPayload payload) {
        try {
            KafkaEnvelope<BookingRestoredPayload> envelope = new KafkaEnvelope<>(
                    "BookingRestored", "1",
                    LocalDateTime.now().toString(),
                    "booking-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.booking.restored",
                    payload.getBookingId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish BookingRestored event", e);
        }
    }

    public void publishWaitlistPromoted(WaitlistPromotedPayload payload) {
        try {
            KafkaEnvelope<WaitlistPromotedPayload> envelope = new KafkaEnvelope<>(
                    "WaitlistPromoted", "1",
                    LocalDateTime.now().toString(),
                    "booking-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("holiday-planner.booking.waitlist-promoted",
                    payload.getBookingId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish WaitlistPromoted event", e);
        }
    }
}
