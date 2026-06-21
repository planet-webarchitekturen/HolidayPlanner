package com.holidayplanner.paymentservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.paymentservice.command.PaymentCommandService;
import com.holidayplanner.paymentservice.repository.PaymentRepository;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.model.Payment;
import com.holidayplanner.shared.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCancelledConsumer {

    private final PaymentCommandService paymentCommandService;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "holiday-planner.booking.cancelled", groupId = "payment-service")
    public void consume(String message) {
        try {
            KafkaEnvelope<BookingCancelledPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<BookingCancelledPayload>>() {});
            BookingCancelledPayload payload = envelope.getPayload();

            Optional<Payment> existing = paymentRepository.findByBookingId(payload.getBookingId());
            if (existing.isEmpty()) {
                log.info("No payment exists for cancelled booking {}, nothing to do", payload.getBookingId());
                return;
            }

            Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                log.info("Payment {} for booking {} already refunded/cancelled, skipping",
                        payment.getId(), payload.getBookingId());
                return;
            }

            String note = "Booking cancelled (" + payload.getCancelledBy() + ") - payment auto-cancelled";
            paymentCommandService.refundPayment(payment.getId(), note);
            log.info("Cancelled payment {} for cancelled booking {} (cancelledBy={})",
                    payment.getId(), payload.getBookingId(), payload.getCancelledBy());
        } catch (Exception e) {
            log.error("Failed to process BookingCancelled event: {}", e.getMessage(), e);
        }
    }
}