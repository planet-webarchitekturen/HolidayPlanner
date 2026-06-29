package com.holidayplanner.organizationservice.kafka;

import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletedPayload;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Terminal step of the delete-organization saga, extracted into its own bean so the DELETED state
 * change and its {@code OrganizationDeleted} outbox event commit in a single transaction.
 *
 * <p>This must be a separate Spring bean (not a private method on
 * {@link OrganizationDeletionCompletionService}) because the completion timer invokes it from a raw
 * executor thread; a {@code @Transactional} method called through the injected proxy gets a real
 * transaction, whereas a self-invoked method would not.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDeletionFinalizer {

    private final OrganizationRepository organizationRepository;
    private final OrganizationEventProducer organizationEventProducer;

    /**
     * Finalizes the deletion only if the organization is still {@code DELETING} (idempotent guard
     * against a rollback or a duplicate timer): sets {@code DELETED} and records the
     * {@code OrganizationDeleted} event in the outbox, atomically.
     *
     * @return {@code true} if the organization was finalized, {@code false} if it was already
     *         finalized, rolled back, or no longer exists
     */
    @Transactional
    public boolean finalizeIfStillDeleting(UUID organizationId) {
        Organization org = organizationRepository.findById(organizationId).orElse(null);
        if (org == null || org.getStatus() != OrganizationStatus.DELETING) {
            return false; // already finalized, rolled back, or organization no longer exists
        }

        org.setStatus(OrganizationStatus.DELETED);
        organizationRepository.save(org);
        organizationEventProducer.publishOrganizationDeleted(
                new OrganizationDeletedPayload(org.getId(), org.getName()));

        log.info("Organization deletion saga completed for {}", organizationId);
        return true;
    }
}
