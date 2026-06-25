package com.holidayplanner.paymentservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.paymentservice.repository.PaymentRepository;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.model.Payment;
import com.holidayplanner.shared.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingCancelledConsumerTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentEventProducer paymentEventProducer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BookingCancelledConsumer consumer() {
        return new BookingCancelledConsumer(paymentRepository, paymentEventProducer, objectMapper);
    }

    private String cancelledMessage(UUID bookingId) throws Exception {
        BookingCancelledPayload payload = new BookingCancelledPayload(
                bookingId, UUID.randomUUID(), UUID.randomUUID(),
                "parent@example.test", "Swimming", "2025-10-01", "term-cancelled",
                UUID.randomUUID(), UUID.randomUUID());
        KafkaEnvelope<BookingCancelledPayload> envelope = new KafkaEnvelope<>(
                "BookingCancelled", "1", "2025-01-01T00:00:00", "booking-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }

    private Payment payment(UUID bookingId, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setBookingId(bookingId);
        p.setStatus(status);
        return p;
    }

    // ── PAID → REFUNDED (pivot crossed — real money) ─────────────────────────

    @Test
    void paidPayment_isSetToRefunded() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(bookingId, PaymentStatus.PAID);
        when(paymentRepository.findByBookingId(bookingId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer().consume(cancelledMessage(bookingId));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void paidPayment_publishesPaymentRefundedEvent() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(bookingId, PaymentStatus.PAID);
        when(paymentRepository.findByBookingId(bookingId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer().consume(cancelledMessage(bookingId));

        verify(paymentEventProducer).publishPaymentRefunded(any());
    }

    // ── PENDING → VOIDED (before pivot — no money exchanged) ─────────────────

    @Test
    void pendingPayment_isSetToVoided() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(bookingId, PaymentStatus.PENDING);
        when(paymentRepository.findByBookingId(bookingId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer().consume(cancelledMessage(bookingId));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
    }

    @Test
    void pendingPayment_doesNotPublishRefundEvent() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(bookingId, PaymentStatus.PENDING);
        when(paymentRepository.findByBookingId(bookingId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer().consume(cancelledMessage(bookingId));

        verify(paymentEventProducer, never()).publishPaymentRefunded(any());
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    void noPaymentFound_savesNothingAndPublishesNothing() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(paymentRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());

        consumer().consume(cancelledMessage(bookingId));

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(paymentEventProducer);
    }

    @Test
    void malformedMessage_isSwallowedWithNoSideEffects() {
        consumer().consume("not-json");

        verifyNoInteractions(paymentRepository, paymentEventProducer);
    }
}
