package com.holidayplanner.paymentservice.command;

import com.holidayplanner.paymentservice.kafka.PaymentEventProducer;
import com.holidayplanner.paymentservice.repository.PaymentRepository;
import com.holidayplanner.shared.kafka.payload.PaymentRefundedPayload;
import com.holidayplanner.shared.model.Payment;
import com.holidayplanner.shared.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;

    @Transactional
    public Payment createPayment(UUID bookingId, UUID organizationId, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setOrganizationId(organizationId);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment markAsPaid(UUID paymentId, String note) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setNote(note);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment refundPayment(UUID paymentId, String note) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        payment.setNote(note);
        Payment saved = paymentRepository.save(payment);

        PaymentRefundedPayload payload = new PaymentRefundedPayload(
                saved.getId(),
                saved.getBookingId(),
                saved.getOrganizationId(),
                null,
                null,
                saved.getAmount());
        paymentEventProducer.publishPaymentRefunded(payload);

        return saved;
    }
}
