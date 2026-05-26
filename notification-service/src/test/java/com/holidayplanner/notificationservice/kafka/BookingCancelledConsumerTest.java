package com.holidayplanner.notificationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.notificationservice.service.NotificationService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingCancelledConsumerTest {

    @Mock
    private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parentCancellationSendsParentCancellationNotification() throws Exception {
        BookingCancelledConsumer consumer = new BookingCancelledConsumer(notificationService, objectMapper);

        consumer.consume(bookingCancelledMessage("parent"));

        verify(notificationService).notifyBookingCancelledByUser(
                "parent@example.test", "Bike Adventure", "2026-06-15T09:00");
        verify(notificationService, never()).notifyBookingCancelledByOwner(
                "parent@example.test", "Bike Adventure", "2026-06-15T09:00");
    }

    @Test
    void eventOwnerCancellationSendsOwnerCancellationNotification() throws Exception {
        BookingCancelledConsumer consumer = new BookingCancelledConsumer(notificationService, objectMapper);

        consumer.consume(bookingCancelledMessage("EVENT_OWNER"));

        verify(notificationService).notifyBookingCancelledByOwner(
                "parent@example.test", "Bike Adventure", "2026-06-15T09:00");
        verify(notificationService, never()).notifyBookingCancelledByUser(
                "parent@example.test", "Bike Adventure", "2026-06-15T09:00");
    }

    @Test
    void termCancellationSendsTermCancelledNotification() throws Exception {
        BookingCancelledConsumer consumer = new BookingCancelledConsumer(notificationService, objectMapper);

        consumer.consume(bookingCancelledMessage("term-cancelled"));

        verify(notificationService).notifyTermCancelled("parent@example.test", "Bike Adventure", "2026-06-15T09:00");
    }

    private String bookingCancelledMessage(String cancelledBy) throws Exception {
        BookingCancelledPayload payload = new BookingCancelledPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "parent@example.test",
                "Bike Adventure",
                "2026-06-15T09:00",
                cancelledBy);
        KafkaEnvelope<BookingCancelledPayload> envelope = new KafkaEnvelope<>(
                "BookingCancelled", "1", LocalDateTime.now().toString(), "booking-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }
}
