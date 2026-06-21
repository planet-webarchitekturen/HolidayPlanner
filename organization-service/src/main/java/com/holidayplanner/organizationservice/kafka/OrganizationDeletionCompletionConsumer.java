package com.holidayplanner.organizationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletedPayload;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDeletionCompletionConsumer {

    private final ObjectMapper objectMapper;
    private final OrganizationRepository organizationRepository;
    private final OrganizationEventProducer organizationEventProducer;
    
    // Track bookings cancelled per organization during deletion saga
    private final Map<UUID, Set<UUID>> cancelledBookingsByOrg = new ConcurrentHashMap<>();

    @KafkaListener(
            topics = "holiday-planner.booking.cancelled",
            groupId = "organization-service-booking-cancelled"
    )
    public void consumeBookingCancelled(String message) {
        try {
            KafkaEnvelope<BookingCancelledPayload> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<KafkaEnvelope<BookingCancelledPayload>>() {});
            BookingCancelledPayload payload = envelope.getPayload();

            // Check if organization is in DELETING state
            Organization org = organizationRepository.findById(payload.getOrganizationId())
                    .orElse(null);
            
            if (org != null && org.getStatus() == OrganizationStatus.DELETING) {
                // Track this booking as cancelled
                cancelledBookingsByOrg.computeIfAbsent(payload.getOrganizationId(), k -> ConcurrentHashMap.newKeySet())
                        .add(payload.getBookingId());
                
                log.info("Tracked cancelled booking {} for organization {} during deletion saga",
                        payload.getBookingId(), payload.getOrganizationId());
                
                // For now, mark organization as DELETED immediately when booking cascade starts
                // In a more sophisticated implementation, you would track all bookings and wait for completion
                org.setStatus(OrganizationStatus.DELETED);
                organizationRepository.save(org);
                
                // Publish completion event
                OrganizationDeletedPayload deletedPayload = new OrganizationDeletedPayload(
                        org.getId(),
                        org.getName()
                );
                organizationEventProducer.publishOrganizationDeleted(deletedPayload);
                
                // Clear tracking
                cancelledBookingsByOrg.remove(payload.getOrganizationId());
                
                log.info("Marked organization {} as DELETED and published OrganizationDeleted event", org.getId());
            }
        } catch (Exception e) {
            log.error("Failed to process BookingCancelled event: {}", e.getMessage(), e);
        }
    }
}

