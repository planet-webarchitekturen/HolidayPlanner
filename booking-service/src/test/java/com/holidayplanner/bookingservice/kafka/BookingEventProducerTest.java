package com.holidayplanner.bookingservice.kafka;

import com.holidayplanner.bookingservice.outbox.OutboxService;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import com.holidayplanner.shared.kafka.payload.BookingRestoredPayload;
import com.holidayplanner.shared.kafka.payload.WaitlistPromotedPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingEventProducerTest {

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private BookingEventProducer producer;

    @Test
    void publishBookingCreatedRecordsOutboxEvent() {
        UUID bookingId = UUID.randomUUID();
        BookingCreatedPayload payload = new BookingCreatedPayload(
                bookingId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CONFIRMED",
                "parent@example.test",
                "Summer Camp",
                "2026-07-01T09:00:00",
                UUID.randomUUID(),
                BigDecimal.TEN);

        producer.publishBookingCreated(payload);

        verify(outboxService).record("Booking", bookingId.toString(),
                "BookingCreated", "holiday-planner.booking.created", payload);
    }

    @Test
    void publishBookingCancelledRecordsOutboxEvent() {
        UUID bookingId = UUID.randomUUID();
        BookingCancelledPayload payload = new BookingCancelledPayload(
                bookingId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "parent@example.test",
                "Summer Camp",
                "2026-07-01T09:00:00",
                "parent",
                UUID.randomUUID(),
                UUID.randomUUID());

        producer.publishBookingCancelled(payload);

        verify(outboxService).record("Booking", bookingId.toString(),
                "BookingCancelled", "holiday-planner.booking.cancelled", payload);
    }

    @Test
    void publishBookingRestoredRecordsOutboxEvent() {
        UUID bookingId = UUID.randomUUID();
        BookingRestoredPayload payload = new BookingRestoredPayload(
                bookingId,
                UUID.randomUUID(),
                UUID.randomUUID());

        producer.publishBookingRestored(payload);

        verify(outboxService).record("Booking", bookingId.toString(),
                "BookingRestored", "holiday-planner.booking.restored", payload);
    }

    @Test
    void publishWaitlistPromotedRecordsOutboxEvent() {
        UUID bookingId = UUID.randomUUID();
        WaitlistPromotedPayload payload = new WaitlistPromotedPayload(
                bookingId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "parent@example.test",
                "Summer Camp",
                "2026-07-01T09:00:00");

        producer.publishWaitlistPromoted(payload);

        verify(outboxService).record("Booking", bookingId.toString(),
                "WaitlistPromoted", "holiday-planner.booking.waitlist-promoted", payload);
    }
}
