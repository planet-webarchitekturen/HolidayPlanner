package com.holidayplanner.organizationservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.PaymentRefundedPayload;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Listens for PaymentRefunded events during an active deletion saga.
 *
 * When a PAID payment is refunded the saga has crossed the pivot point: money has left
 * the system and compensation (rollback) is no longer possible for that payment.
 * This consumer marks the pivot so that rollbackDeletion() can reject the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundedConsumer {

    private final ObjectMapper objectMapper;
    private final OrganizationRepository organizationRepository;
    private final OrganizationDeletionCompletionService completionService;

    @KafkaListener(
            topics = "holiday-planner.payment.refunded",
            groupId = "organization-service-payment-refunded"
    )
    public void consume(String message) {
        try {
            KafkaEnvelope<PaymentRefundedPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<PaymentRefundedPayload>>() {});
            PaymentRefundedPayload payload = envelope.getPayload();

            if (payload.getOrganizationId() == null) {
                return;
            }

            Organization org = organizationRepository.findById(payload.getOrganizationId()).orElse(null);
            if (org != null && org.getStatus() == OrganizationStatus.DELETING) {
                completionService.markPivotCrossed(payload.getOrganizationId());
            }
        } catch (Exception e) {
            log.error("Failed to process PaymentRefunded event: {}", e.getMessage(), e);
        }
    }
}
