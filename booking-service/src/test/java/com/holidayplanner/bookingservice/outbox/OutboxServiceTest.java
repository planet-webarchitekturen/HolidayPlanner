package com.holidayplanner.bookingservice.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import com.holidayplanner.shared.model.BookingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordStoresSerializedKafkaEnvelope() throws Exception {
        OutboxService service = new OutboxService(repository, objectMapper);
        UUID bookingId = UUID.randomUUID();
        BookingCreatedPayload payload = new BookingCreatedPayload(
                bookingId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BookingStatus.CONFIRMED,
                "parent@example.test",
                "Summer Camp",
                "2026-07-01T09:00:00",
                null,
                null,
                UUID.randomUUID(),
                BigDecimal.TEN);

        service.record("Booking", bookingId.toString(),
                "BookingCreated", "holiday-planner.booking.created", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());

        OutboxEvent event = captor.getValue();
        assertThat(event.getAggregateType()).isEqualTo("Booking");
        assertThat(event.getAggregateId()).isEqualTo(bookingId.toString());
        assertThat(event.getEventType()).isEqualTo("BookingCreated");
        assertThat(event.getTopic()).isEqualTo("holiday-planner.booking.created");
        assertThat(event.getPartitionKey()).isEqualTo(bookingId.toString());
        assertThat(event.isProcessed()).isFalse();
        assertThat(event.getCreatedAt()).isNotNull();

        JsonNode envelope = objectMapper.readTree(event.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("BookingCreated");
        assertThat(envelope.get("version").asText()).isEqualTo("1");
        assertThat(envelope.get("source").asText()).isEqualTo("booking-service");
        assertThat(envelope.get("payload").get("bookingId").asText()).isEqualTo(bookingId.toString());
    }
}
