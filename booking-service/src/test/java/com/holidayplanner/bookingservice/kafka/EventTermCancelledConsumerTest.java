package com.holidayplanner.bookingservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.bookingservice.command.BookingCommandService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.EventTermCancelledPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EventTermCancelledConsumerTest {

    @Mock
    private BookingCommandService bookingCommandService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cancelsAllBookingsForTheTerm() throws Exception {
        EventTermCancelledConsumer consumer = new EventTermCancelledConsumer(bookingCommandService, objectMapper);
        UUID eventTermId = UUID.randomUUID();
        String message = eventTermCancelledMessage(eventTermId);

        consumer.consume(message);

        verify(bookingCommandService).cancelAllBookings(eventTermId);
    }

    @Test
    void malformedMessageIsSwallowedAndDoesNotCancel() {
        EventTermCancelledConsumer consumer = new EventTermCancelledConsumer(bookingCommandService, objectMapper);

        consumer.consume("not-json");

        verifyNoInteractions(bookingCommandService);
    }

    private String eventTermCancelledMessage(UUID eventTermId) throws Exception {
        EventTermCancelledPayload payload = new EventTermCancelledPayload(
                eventTermId, "Bike Adventure", "2026-06-23T09:00", UUID.randomUUID(),
                List.of("caregiver@example.test"), "SYSTEM");
        KafkaEnvelope<EventTermCancelledPayload> envelope = new KafkaEnvelope<>(
                "EventTermCancelled", "1", LocalDateTime.now().toString(), "event-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }
}
