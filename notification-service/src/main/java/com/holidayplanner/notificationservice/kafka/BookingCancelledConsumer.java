package com.holidayplanner.notificationservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.notificationservice.service.NotificationService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCancelledConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "holiday-planner.booking.cancelled", groupId = "notification-service")
    public void consume(String message) {
        try {
            KafkaEnvelope<BookingCancelledPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<BookingCancelledPayload>>() {});
            BookingCancelledPayload payload = envelope.getPayload();
            String cancelledBy = payload.getCancelledBy();
            if ("EVENT_OWNER".equalsIgnoreCase(cancelledBy) || "event-owner".equalsIgnoreCase(cancelledBy)) {
                notificationService.notifyBookingCancelledByOwner(
                        payload.getParentEmail(),
                        payload.getEventName(),
                        payload.getTermDate());
            } else if ("TERM_CANCELLED".equalsIgnoreCase(cancelledBy) || "term-cancelled".equalsIgnoreCase(cancelledBy)) {
                notificationService.notifyTermCancelled(
                        payload.getParentEmail(),
                        payload.getEventName(),
                        payload.getTermDate());
            } else if ("USER".equalsIgnoreCase(cancelledBy) || "PARENT".equalsIgnoreCase(cancelledBy)) {
                notificationService.notifyBookingCancelledByUser(
                        payload.getParentEmail(),
                        payload.getEventName(),
                        payload.getTermDate());
            } else {
                log.warn("Ignoring BookingCancelled event with unsupported cancelledBy: {}", cancelledBy);
            }
        } catch (Exception e) {
            log.error("Failed to process BookingCancelled event: {}", e.getMessage());
        }
    }
}
