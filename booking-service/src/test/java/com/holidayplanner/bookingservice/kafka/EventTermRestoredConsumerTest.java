package com.holidayplanner.bookingservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.bookingservice.command.BookingCommandService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.EventTermRestoredPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EventTermRestoredConsumerTest {

    @Mock
    private BookingCommandService bookingCommandService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventTermRestoredConsumer consumer() {
        return new EventTermRestoredConsumer(bookingCommandService, objectMapper);
    }

    private String termRestoredMessage(UUID termId, UUID orgId) throws Exception {
        EventTermRestoredPayload payload = new EventTermRestoredPayload(termId, orgId);
        KafkaEnvelope<EventTermRestoredPayload> envelope = new KafkaEnvelope<>(
                "EventTermRestored", "1", "2025-01-01T00:00:00", "event-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }

    @Test
    void termRestored_callsRestoreAllBookingsForTermWithCorrectIds() throws Exception {
        UUID termId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        consumer().consume(termRestoredMessage(termId, orgId));

        verify(bookingCommandService).restoreAllBookingsForTerm(termId, orgId);
    }

    @Test
    void malformedMessage_isSwallowedWithNoSideEffects() {
        consumer().consume("not-json");

        verifyNoInteractions(bookingCommandService);
    }
}
