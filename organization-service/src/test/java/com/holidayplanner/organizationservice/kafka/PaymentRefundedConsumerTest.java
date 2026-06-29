package com.holidayplanner.organizationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.PaymentRefundedPayload;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the PaymentRefunded listener that marks the saga's refund pivot: once a PAID payment
 * is refunded for a DELETING org, rollback must be blocked.
 */
@ExtendWith(MockitoExtension.class)
class PaymentRefundedConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationDeletionCompletionService completionService;

    private PaymentRefundedConsumer consumer() {
        return new PaymentRefundedConsumer(objectMapper, organizationRepository, completionService);
    }

    private String refundedMessage(UUID organizationId) throws Exception {
        PaymentRefundedPayload payload = new PaymentRefundedPayload();
        payload.setPaymentId(UUID.randomUUID());
        payload.setOrganizationId(organizationId);
        payload.setAmount(new BigDecimal("15.00"));
        KafkaEnvelope<PaymentRefundedPayload> envelope = new KafkaEnvelope<>(
                "PaymentRefunded", "1", "2026-06-30T10:00:00", "payment-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }

    @Test
    void consume_whenOrgIsDeleting_marksPivotCrossed() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(orgId);
        org.setStatus(OrganizationStatus.DELETING);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        consumer().consume(refundedMessage(orgId));

        verify(completionService).markPivotCrossed(orgId);
    }

    @Test
    void consume_whenOrgIsNotDeleting_doesNotMarkPivot() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(orgId);
        org.setStatus(OrganizationStatus.ACTIVE);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        consumer().consume(refundedMessage(orgId));

        verify(completionService, never()).markPivotCrossed(any());
    }

    @Test
    void consume_whenOrganizationIdMissing_ignored() throws Exception {
        consumer().consume(refundedMessage(null));

        verify(organizationRepository, never()).findById(any());
        verify(completionService, never()).markPivotCrossed(any());
    }
}
