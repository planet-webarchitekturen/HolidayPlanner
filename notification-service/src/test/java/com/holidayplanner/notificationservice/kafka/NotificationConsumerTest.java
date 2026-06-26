package com.holidayplanner.notificationservice.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.notificationservice.service.NotificationService;
import com.holidayplanner.notificationservice.service.ProcessedEventService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import com.holidayplanner.shared.kafka.payload.PaymentRefundedPayload;
import com.holidayplanner.shared.model.BookingStatus;
import com.holidayplanner.shared.model.CancelledBy;
import com.holidayplanner.shared.model.PaymentMethod;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the notification-service Kafka consumers. They deserialize a real
 * {@link KafkaEnvelope} JSON message and verify the consumer delegates to the matching
 * {@link NotificationService} method. {@link ProcessedEventService} is stubbed so the idempotent
 * action actually runs (its real implementation only de-duplicates via the database).
 */
class NotificationConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificationService notificationService;
    private ProcessedEventService processedEventService;

    @BeforeEach
    void setUp() throws Exception {
        notificationService = mock(NotificationService.class);
        processedEventService = mock(ProcessedEventService.class);
        // Run the wrapped action instead of consulting the (database-backed) dedup store.
        doAnswer(invocation -> {
            invocation.getArgument(1, ProcessedEventService.ThrowingRunnable.class).run();
            return null;
        }).when(processedEventService).process(any(UUID.class), any());
    }

    @Test
    void bookingCreatedConsumer_notifiesParentOfConfirmedBooking() throws Exception {
        BookingCreatedPayload payload = new BookingCreatedPayload(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BookingStatus.CONFIRMED, "parent@example.test", "Bike Adventure",
                "2026-06-15T09:00", "Main gate", PaymentMethod.BANK_TRANSFER,
                UUID.randomUUID(), new BigDecimal("12.50"));
        String message = envelope("holiday-planner.booking.created", payload);

        new BookingCreatedConsumer(notificationService, processedEventService, objectMapper)
                .consume(message);

        verify(notificationService).notifyBookingCreated(
                eq("parent@example.test"), eq("Bike Adventure"), eq("2026-06-15T09:00"),
                eq(BookingStatus.CONFIRMED), eq("Main gate"), eq(PaymentMethod.BANK_TRANSFER),
                eq(new BigDecimal("12.50")));
    }

    @Test
    void bookingCancelledConsumer_notifiesParentOfCancellation() throws Exception {
        BookingCancelledPayload payload = new BookingCancelledPayload(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "parent@example.test", "Bike Adventure", "2026-06-15T09:00", CancelledBy.USER);
        String message = envelope("holiday-planner.booking.cancelled", payload);

        new BookingCancelledConsumer(notificationService, processedEventService, objectMapper)
                .consume(message);

        verify(notificationService).notifyBookingCancelled(
                eq("parent@example.test"), eq("Bike Adventure"), eq("2026-06-15T09:00"),
                eq(CancelledBy.USER));
    }

    @Test
    void paymentRefundedConsumer_notifiesParentOfRefund() throws Exception {
        PaymentRefundedPayload payload = new PaymentRefundedPayload(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "parent@example.test", "Bike Adventure", new BigDecimal("15.00"));
        String message = envelope("holiday-planner.payment.refunded", payload);

        new PaymentRefundedConsumer(notificationService, processedEventService, objectMapper)
                .consume(message);

        verify(notificationService).notifyRefund(
                eq("parent@example.test"), eq("Bike Adventure"), eq(new BigDecimal("15.00")));
    }

    private <T> String envelope(String eventType, T payload) throws Exception {
        KafkaEnvelope<T> envelope =
                new KafkaEnvelope<>(eventType, "1", "2026-06-15T09:00:00Z", "test", payload);
        return objectMapper.writeValueAsString(envelope);
    }
}
