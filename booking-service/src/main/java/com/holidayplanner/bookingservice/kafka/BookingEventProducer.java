package com.holidayplanner.bookingservice.kafka;

import com.holidayplanner.bookingservice.outbox.OutboxService;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import com.holidayplanner.shared.kafka.payload.WaitlistPromotedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingEventProducer {

    private static final String TOPIC_BOOKING_CREATED = "holiday-planner.booking.created";
    private static final String TOPIC_BOOKING_CANCELLED = "holiday-planner.booking.cancelled";
    private static final String TOPIC_WAITLIST_PROMOTED = "holiday-planner.booking.waitlist-promoted";

    private final OutboxService outboxService;

    public void publishBookingCreated(BookingCreatedPayload payload) {
        outboxService.record("Booking", payload.getBookingId().toString(),
                "BookingCreated", TOPIC_BOOKING_CREATED, payload);
    }

    public void publishBookingCancelled(BookingCancelledPayload payload) {
        outboxService.record("Booking", payload.getBookingId().toString(),
                "BookingCancelled", TOPIC_BOOKING_CANCELLED, payload);
    }

    public void publishWaitlistPromoted(WaitlistPromotedPayload payload) {
        outboxService.record("Booking", payload.getBookingId().toString(),
                "WaitlistPromoted", TOPIC_WAITLIST_PROMOTED, payload);
    }
}
