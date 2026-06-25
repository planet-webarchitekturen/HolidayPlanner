package com.holidayplanner.paymentservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.paymentservice.command.PaymentCommandService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingRestoredPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BookingRestoredConsumerTest {

    @Mock
    private PaymentCommandService paymentCommandService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BookingRestoredConsumer consumer() {
        return new BookingRestoredConsumer(objectMapper, paymentCommandService);
    }

    private String bookingRestoredMessage(UUID bookingId) throws Exception {
        BookingRestoredPayload payload =
                new BookingRestoredPayload(bookingId, UUID.randomUUID(), UUID.randomUUID());
        KafkaEnvelope<BookingRestoredPayload> envelope = new KafkaEnvelope<>(
                "BookingRestored", "1", "2025-01-01T00:00:00", "booking-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }

    @Test
    void bookingRestored_callsRestoreVoidedPaymentWithCorrectBookingId() throws Exception {
        UUID bookingId = UUID.randomUUID();

        consumer().consume(bookingRestoredMessage(bookingId));

        verify(paymentCommandService).restoreVoidedPayment(bookingId);
    }

    @Test
    void malformedMessage_isSwallowedWithNoSideEffects() {
        consumer().consume("not-json");

        verifyNoInteractions(paymentCommandService);
    }
}
