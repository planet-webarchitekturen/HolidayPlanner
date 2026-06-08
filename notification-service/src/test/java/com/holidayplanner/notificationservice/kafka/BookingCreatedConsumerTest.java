package com.holidayplanner.notificationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.notificationservice.service.NotificationService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingCreatedConsumerTest {

    @Mock
    private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void confirmedBookingSendsConfirmationNotification() throws Exception {
        BookingCreatedConsumer consumer = new BookingCreatedConsumer(notificationService, objectMapper);
        String message = bookingCreatedMessage("CONFIRMED");

        consumer.consume(message);

        verify(notificationService).notifyBookingConfirmed("parent@example.test", "Bike Adventure", "2026-06-15T09:00");
        verify(notificationService, never()).notifyBookingWaitlisted(
                "parent@example.test", "Bike Adventure", "2026-06-15T09:00");
    }

    @Test
    void waitlistedBookingSendsWaitlistNotification() throws Exception {
        BookingCreatedConsumer consumer = new BookingCreatedConsumer(notificationService, objectMapper);
        String message = bookingCreatedMessage("WAITLISTED");

        consumer.consume(message);

        verify(notificationService).notifyBookingWaitlisted("parent@example.test", "Bike Adventure", "2026-06-15T09:00");
        verify(notificationService, never()).notifyBookingConfirmed(
                "parent@example.test", "Bike Adventure", "2026-06-15T09:00");
    }

    private String bookingCreatedMessage(String status) throws Exception {
        BookingCreatedPayload payload = new BookingCreatedPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                "parent@example.test",
                "Bike Adventure",
                "2026-06-15T09:00",
                UUID.randomUUID(),
                new BigDecimal("15.00"));
        KafkaEnvelope<BookingCreatedPayload> envelope = new KafkaEnvelope<>(
                "BookingCreated", "1", LocalDateTime.now().toString(), "booking-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }
}
