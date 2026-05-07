package com.holidayplanner.paymentservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.paymentservice.repository.PaymentRepository;
import com.holidayplanner.paymentservice.command.PaymentCommandService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCreatedConsumer {

    private final PaymentCommandService paymentCommandService;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "holiday-planner.booking.created", groupId = "payment-service")
    public void consume(String message) {
        try {
            KafkaEnvelope<BookingCreatedPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<BookingCreatedPayload>>() {});
            BookingCreatedPayload payload = envelope.getPayload();

            if (!"CONFIRMED".equals(payload.getStatus())) {
                return;
            }

            boolean alreadyExists = paymentRepository.findByBookingId(payload.getBookingId()).isPresent();
            if (alreadyExists) {
                log.warn("Payment already exists for booking {}, skipping", payload.getBookingId());
                return;
            }

            paymentCommandService.createPayment(payload.getBookingId(), payload.getOrganizationId(), payload.getAmount());
            log.info("Created payment for booking {}", payload.getBookingId());
        } catch (Exception e) {
            log.error("Failed to process BookingCreated event: {}", e.getMessage());
        }
    }
}
