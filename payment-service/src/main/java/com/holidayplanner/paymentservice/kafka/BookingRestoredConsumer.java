package com.holidayplanner.paymentservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.paymentservice.command.PaymentCommandService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingRestoredPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingRestoredConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentCommandService paymentCommandService;

    @KafkaListener(
            topics = "holiday-planner.booking.restored",
            groupId = "payment-service-booking-restored"
    )
    public void consume(String message) {
        try {
            KafkaEnvelope<BookingRestoredPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<BookingRestoredPayload>>() {});
            BookingRestoredPayload payload = envelope.getPayload();
            paymentCommandService.restoreVoidedPayment(payload.getBookingId());
            log.info("Processed BookingRestored for booking {}", payload.getBookingId());
        } catch (Exception e) {
            log.error("Failed to process BookingRestored event: {}", e.getMessage(), e);
        }
    }
}
