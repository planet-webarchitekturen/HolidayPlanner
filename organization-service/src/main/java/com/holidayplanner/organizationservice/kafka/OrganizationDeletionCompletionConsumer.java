package com.holidayplanner.organizationservice.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDeletionCompletionConsumer {

    private final ObjectMapper objectMapper;
    private final OrganizationRepository organizationRepository;
    private final OrganizationDeletionCompletionService completionService;

    @KafkaListener(
            topics = "holiday-planner.booking.cancelled",
            groupId = "organization-service-booking-cancelled"
    )
    public void consume(String message) {
        try {
            KafkaEnvelope<BookingCancelledPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<BookingCancelledPayload>>() {});
            BookingCancelledPayload payload = envelope.getPayload();

            if (payload.getOrganizationId() == null) {
                return;
            }

            Organization org = organizationRepository.findById(payload.getOrganizationId()).orElse(null);
            if (org != null && org.getStatus() == OrganizationStatus.DELETING) {
                log.info("Observed cancelled booking {} for organization {} during deletion saga",
                        payload.getBookingId(), payload.getOrganizationId());
                completionService.onActivitySeen(payload.getOrganizationId());
            }
        } catch (Exception e) {
            log.error("Failed to process BookingCancelled event: {}", e.getMessage(), e);
        }
    }
}