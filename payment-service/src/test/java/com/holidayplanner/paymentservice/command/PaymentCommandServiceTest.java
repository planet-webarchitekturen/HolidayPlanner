package com.holidayplanner.paymentservice.command;

import com.holidayplanner.paymentservice.kafka.PaymentEventProducer;
import com.holidayplanner.paymentservice.repository.PaymentRepository;
import com.holidayplanner.shared.kafka.payload.PaymentRefundedPayload;
import com.holidayplanner.shared.model.Payment;
import com.holidayplanner.shared.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @InjectMocks
    private PaymentCommandService paymentCommandService;

    @Test
    void createPaymentCreatesPendingPayment() {
        UUID bookingId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        BigDecimal amount = new BigDecimal("30.00");

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentCommandService.createPayment(bookingId, organizationId, amount);

        assertEquals(bookingId, payment.getBookingId());
        assertEquals(organizationId, payment.getOrganizationId());
        assertEquals(amount, payment.getAmount());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void refundPaymentPublishesRefundEvent() {
        UUID paymentId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        UUID bookingId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setBookingId(bookingId);
        payment.setOrganizationId(organizationId);
        payment.setAmount(new BigDecimal("30.00"));
        payment.setStatus(PaymentStatus.PAID);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment refunded = paymentCommandService.refundPayment(paymentId, "Refunded after cancellation");

        assertEquals(PaymentStatus.REFUNDED, refunded.getStatus());
        assertEquals("Refunded after cancellation", refunded.getNote());

        ArgumentCaptor<PaymentRefundedPayload> captor = ArgumentCaptor.forClass(PaymentRefundedPayload.class);
        verify(paymentEventProducer).publishPaymentRefunded(captor.capture());
        assertEquals(paymentId, captor.getValue().getPaymentId());
        assertEquals(bookingId, captor.getValue().getBookingId());
        assertEquals(organizationId, captor.getValue().getOrganizationId());
    }
}
