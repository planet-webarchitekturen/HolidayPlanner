package com.holidayplanner.paymentservice.command;

import com.holidayplanner.paymentservice.kafka.PaymentEventProducer;
import com.holidayplanner.paymentservice.repository.PaymentRepository;
import com.holidayplanner.shared.kafka.payload.PaymentRefundedPayload;
import com.holidayplanner.shared.model.Payment;
import com.holidayplanner.shared.model.PaymentStatus;
import com.holidayplanner.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    public Payment createPayment(UUID bookingId, UUID organizationId, BigDecimal amount,
            String parentEmail, String eventName) {
        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setOrganizationId(organizationId);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setParentEmail(parentEmail);
        payment.setEventName(eventName);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment markAsPaid(UUID paymentId, String note) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        UUID currentOrgId = SecurityUtils.getCurrentOrganizationId();
        if (currentOrgId != null && payment.getOrganizationId() != null
                && !currentOrgId.equals(payment.getOrganizationId())) {
            throw new AccessDeniedException("Payment belongs to a different organization");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a PENDING payment can be marked as PAID; payment " + paymentId + " is " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setNote(note);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment refundPayment(UUID paymentId, String note) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        UUID currentOrgId = SecurityUtils.getCurrentOrganizationId();
        if (currentOrgId != null && payment.getOrganizationId() != null
                && !currentOrgId.equals(payment.getOrganizationId())) {
            throw new AccessDeniedException("Payment belongs to a different organization");
        }

        // Idempotent: re-refunding an already-REFUNDED payment is a no-op (no duplicate event).
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return payment;
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException(
                    "Only a PAID payment can be refunded; payment " + paymentId + " is " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        payment.setNote(note);
        Payment saved = paymentRepository.save(payment);

        PaymentRefundedPayload payload = new PaymentRefundedPayload(
                saved.getId(),
                saved.getBookingId(),
                saved.getOrganizationId(),
                saved.getParentEmail(),
                saved.getEventName(),
                saved.getAmount());
        paymentEventProducer.publishPaymentRefunded(payload);

        return saved;
    }
}
