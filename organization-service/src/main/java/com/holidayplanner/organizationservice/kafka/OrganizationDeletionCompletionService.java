package com.holidayplanner.organizationservice.kafka;

import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletedPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletionRolledBackPayload;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDeletionCompletionService {

    private static final long QUIET_PERIOD_SECONDS = 5;
    private static final long MAX_WAIT_SECONDS = 20;

    private final OrganizationRepository organizationRepository;
    private final OrganizationEventProducer organizationEventProducer;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, ScheduledFuture<?>> pendingFinalization = new ConcurrentHashMap<>();

    /** Orgs for which at least one PAID payment has been refunded — the saga has passed the pivot point. */
    private final Set<UUID> pivotCrossed = ConcurrentHashMap.newKeySet();

    /** Call when the deletion saga starts, to guarantee eventual completion. */
    public void scheduleFallback(UUID organizationId) {
        reschedule(organizationId, MAX_WAIT_SECONDS);
    }

    /** Call whenever a BookingCancelled event is observed for this organization. */
    public void onActivitySeen(UUID organizationId) {
        reschedule(organizationId, QUIET_PERIOD_SECONDS);
    }

    /**
     * Called by PaymentRefundedConsumer when a PAID payment is refunded for a DELETING org.
     * After this point the saga has passed the refund pivot and cannot be compensated.
     */
    public void markPivotCrossed(UUID organizationId) {
        pivotCrossed.add(organizationId);
        log.info("Refund pivot crossed for organization {}", organizationId);
    }

    /** Returns true if at least one PAID payment has been refunded for this org's deletion saga. */
    public boolean isPivotCrossed(UUID organizationId) {
        return pivotCrossed.contains(organizationId);
    }

    /**
     * Cancel the pending finalization timer for an org being rolled back.
     * Must be called before the org status is changed to prevent a late timer
     * from setting it to DELETED after a successful rollback.
     */
    public void cancelPendingFinalization(UUID organizationId) {
        ScheduledFuture<?> pending = pendingFinalization.remove(organizationId);
        if (pending != null) {
            pending.cancel(false);
        }
        pivotCrossed.remove(organizationId);
    }

    private void reschedule(UUID organizationId, long delaySeconds) {
        ScheduledFuture<?> previous = pendingFinalization.put(organizationId, scheduler.schedule(
                () -> finalizeIfStillDeleting(organizationId),
                delaySeconds, TimeUnit.SECONDS));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void finalizeIfStillDeleting(UUID organizationId) {
        try {
            pendingFinalization.remove(organizationId);
            Organization org = organizationRepository.findById(organizationId).orElse(null);
            if (org == null || org.getStatus() != OrganizationStatus.DELETING) {
                return; // already finalized, or organization no longer exists
            }

            org.setStatus(OrganizationStatus.DELETED);
            organizationRepository.save(org);

            OrganizationDeletedPayload payload = new OrganizationDeletedPayload(org.getId(), org.getName());
            organizationEventProducer.publishOrganizationDeleted(payload);

            log.info("Organization deletion saga completed for {} (no further activity for {}s)",
                    organizationId, QUIET_PERIOD_SECONDS);
        } catch (Exception e) {
            log.error("Failed to finalize organization deletion for {}: {}", organizationId, e.getMessage(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}