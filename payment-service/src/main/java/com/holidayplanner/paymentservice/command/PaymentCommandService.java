package com.holidayplanner.paymentservice.command;

import com.holidayplanner.paymentservice.kafka.PaymentEventProducer;
import com.holidayplanner.paymentservice.repository.PaymentRepository;
import com.holidayplanner.shared.kafka.payload.PaymentRefundedPayload;
import com.holidayplanner.shared.model.Payment;
import com.holidayplanner.shared.model.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import com.holidayplanner.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
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

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setNote(note);
        return paymentRepository.save(payment);
    }

    @Transactional
    public void restoreVoidedPayment(UUID bookingId) {
        paymentRepository.findByBookingId(bookingId).ifPresentOrElse(payment -> {
            if (payment.getStatus() == PaymentStatus.VOIDED) {
                payment.setStatus(PaymentStatus.PENDING);
                paymentRepository.save(payment);
                log.info("Restored VOIDED payment {} back to PENDING for booking {}", payment.getId(), bookingId);
            } else {
                log.warn("Cannot restore payment {} for booking {} — status is {} not VOIDED",
                        payment.getId(), bookingId, payment.getStatus());
            }
        }, () -> log.warn("No payment found for booking {} during rollback restore", bookingId));
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
