package com.holidayplanner.organizationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the BookingCancelled listener that drives the deletion saga's quiet-period timer:
 * it nudges the timer only for organizations that are actually being deleted.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationDeletionCompletionConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationDeletionCompletionService completionService;

    private OrganizationDeletionCompletionConsumer consumer() {
        return new OrganizationDeletionCompletionConsumer(objectMapper, organizationRepository, completionService);
    }

    private String bookingCancelledMessage(UUID organizationId) throws Exception {
        BookingCancelledPayload payload = new BookingCancelledPayload();
        payload.setBookingId(UUID.randomUUID());
        payload.setOrganizationId(organizationId);
        KafkaEnvelope<BookingCancelledPayload> envelope = new KafkaEnvelope<>(
                "BookingCancelled", "1", "2026-06-30T10:00:00", "booking-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }

    @Test
    void consume_whenOrgIsDeleting_nudgesCompletionTimer() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(orgId);
        org.setStatus(OrganizationStatus.DELETING);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        consumer().consume(bookingCancelledMessage(orgId));

        verify(completionService).onActivitySeen(orgId);
    }

    @Test
    void consume_whenOrgIsNotDeleting_ignored() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(orgId);
        org.setStatus(OrganizationStatus.ACTIVE);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        consumer().consume(bookingCancelledMessage(orgId));

        verify(completionService, never()).onActivitySeen(any());
    }

    @Test
    void consume_whenOrganizationIdMissing_ignored() throws Exception {
        consumer().consume(bookingCancelledMessage(null));

        verify(organizationRepository, never()).findById(any());
        verify(completionService, never()).onActivitySeen(any());
    }
}
