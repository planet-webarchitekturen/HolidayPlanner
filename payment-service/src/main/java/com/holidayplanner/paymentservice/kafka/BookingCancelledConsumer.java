package com.holidayplanner.paymentservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.paymentservice.repository.PaymentRepository;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCancelledConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "holiday-planner.booking.cancelled", groupId = "payment-service")
    @Transactional
    public void consume(String message) {
        try {
            KafkaEnvelope<BookingCancelledPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<BookingCancelledPayload>>() {});
            BookingCancelledPayload payload = envelope.getPayload();

            paymentRepository.findByBookingId(payload.getBookingId()).ifPresentOrElse(payment -> {
                if (payment.getStatus() == PaymentStatus.PAID) {
                    payment.setStatus(PaymentStatus.REFUNDED);
                    paymentRepository.save(payment);
                    paymentEventProducer.publishPaymentRefunded(
                            new com.holidayplanner.shared.kafka.payload.PaymentRefundedPayload(
                                    payment.getId(),
                                    payment.getBookingId(),
                                    payment.getOrganizationId(),
                                    payment.getParentEmail(),
                                    payment.getEventName(),
                                    payment.getAmount()));
                    log.info("Refunded payment {} for cancelled booking {}", payment.getId(), payload.getBookingId());
                } else if (payment.getStatus() == PaymentStatus.PENDING) {
                    // PENDING means no money was exchanged — cancel without a refund event.
                    payment.setStatus(PaymentStatus.VOIDED);
                    paymentRepository.save(payment);
                    log.info("Voided pending payment {} for booking {}", payment.getId(), payload.getBookingId());
                }
            }, () -> log.warn("No payment found for cancelled booking {}", payload.getBookingId()));

        } catch (Exception e) {
            log.error("Failed to process BookingCancelled event: {}", e.getMessage());
        }
    }
}
