package com.holidayplanner.notificationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.notificationservice.service.NotificationService;
import com.holidayplanner.notificationservice.service.ProcessedEventService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingCancelledConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventService processedEventService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ProcessedEventService.ThrowingRunnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(processedEventService).process(any(), any());
    }

    @Test
    void parentCancellationDelegatesToNotificationService() throws Exception {
        BookingCancelledConsumer consumer = new BookingCancelledConsumer(notificationService, processedEventService, objectMapper);

        consumer.consume(bookingCancelledMessage("parent"));

        verify(notificationService).notifyBookingCancelled(
                "parent@example.test", "Bike Adventure", "2026-06-15T09:00", "parent");
    }

    @Test
    void eventOwnerCancellationDelegatesToNotificationService() throws Exception {
        BookingCancelledConsumer consumer = new BookingCancelledConsumer(notificationService, processedEventService, objectMapper);

        consumer.consume(bookingCancelledMessage("EVENT_OWNER"));

        verify(notificationService).notifyBookingCancelled(
                "parent@example.test", "Bike Adventure", "2026-06-15T09:00", "EVENT_OWNER");
    }

    @Test
    void termCancellationDelegatesToNotificationService() throws Exception {
        BookingCancelledConsumer consumer = new BookingCancelledConsumer(notificationService, processedEventService, objectMapper);

        consumer.consume(bookingCancelledMessage("term-cancelled"));

        verify(notificationService).notifyBookingCancelled(
                "parent@example.test", "Bike Adventure", "2026-06-15T09:00", "term-cancelled");
    }

    private String bookingCancelledMessage(String cancelledBy) throws Exception {
        BookingCancelledPayload payload = new BookingCancelledPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "parent@example.test",
                "Bike Adventure",
                "2026-06-15T09:00",
                cancelledBy,
                UUID.randomUUID(),   // organizationId
                UUID.randomUUID());  // eventId
        KafkaEnvelope<BookingCancelledPayload> envelope = new KafkaEnvelope<>(
                "BookingCancelled", "1", LocalDateTime.now().toString(), "booking-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }
}
