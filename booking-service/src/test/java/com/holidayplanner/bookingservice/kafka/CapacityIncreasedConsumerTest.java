package com.holidayplanner.bookingservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.bookingservice.command.BookingCommandService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.CapacityIncreasedPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CapacityIncreasedConsumerTest {

    @Mock
    private BookingCommandService bookingCommandService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void capacityIncreasedPromotesWaitlistByAddedSlots() throws Exception {
        CapacityIncreasedConsumer consumer = new CapacityIncreasedConsumer(bookingCommandService, objectMapper);
        UUID eventTermId = UUID.randomUUID();
        String message = capacityIncreasedMessage(eventTermId, 2, 4);

        consumer.consume(message);

        verify(bookingCommandService).promoteFromWaitingList(eventTermId, 2);
    }

    @Test
    void malformedMessageIsSwallowedAndDoesNotPromote() {
        CapacityIncreasedConsumer consumer = new CapacityIncreasedConsumer(bookingCommandService, objectMapper);

        consumer.consume("not-json");

        verifyNoInteractions(bookingCommandService);
    }

    @Test
    void zeroAddedSlotsPromotesNone() throws Exception {
        CapacityIncreasedConsumer consumer = new CapacityIncreasedConsumer(bookingCommandService, objectMapper);
        UUID eventTermId = UUID.randomUUID();
        String message = capacityIncreasedMessage(eventTermId, 0, 5);

        consumer.consume(message);

        verify(bookingCommandService).promoteFromWaitingList(eventTermId, 0);
        verify(bookingCommandService, never()).promoteFromWaitingList(eventTermId, 1);
    }

    private String capacityIncreasedMessage(UUID eventTermId, int addedSlots, int newMax) throws Exception {
        CapacityIncreasedPayload payload = new CapacityIncreasedPayload(eventTermId, addedSlots, newMax);
        KafkaEnvelope<CapacityIncreasedPayload> envelope = new KafkaEnvelope<>(
                "CapacityIncreased", "1", LocalDateTime.now().toString(), "event-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }
}
