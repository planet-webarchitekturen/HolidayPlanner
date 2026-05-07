package com.holidayplanner.paymentservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.PaymentRefundedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishPaymentRefunded(PaymentRefundedPayload payload) {
        try {
            KafkaEnvelope<PaymentRefundedPayload> envelope = new KafkaEnvelope<>(
                    "PaymentRefunded", "1",
                    LocalDateTime.now().toString(),
                    "payment-service", payload);
            String json = objectMapper.writeValueAsString(envelope);
            String key = payload.getPaymentId().toString();
            kafkaTemplate.send("holiday-planner.payment.refunded", key, json);
        } catch (Exception e) {
            log.error("Failed to publish PaymentRefunded event", e);
        }
    }
}
