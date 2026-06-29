package com.holidayplanner.organizationservice.command;

import com.holidayplanner.organizationservice.exception.NotFoundException;
import com.holidayplanner.organizationservice.kafka.OrganizationDeletionCompletionService;
import com.holidayplanner.organizationservice.kafka.OrganizationEventProducer;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.organizationservice.repository.SponsorRepository;
import com.holidayplanner.organizationservice.repository.TeamMemberRepository;
import com.holidayplanner.shared.kafka.payload.OrganizationCreatedPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletionRolledBackPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletionStartedPayload;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import com.holidayplanner.shared.model.Sponsor;
import com.holidayplanner.shared.model.TeamMember;
import com.holidayplanner.shared.model.TeamMemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Write side (CQRS command) for organizations. Each mutating method runs in its own transaction so the
 * state change and its outbox event commit atomically (the event is recorded via
 * {@link OrganizationEventProducer} into the transactional outbox, never sent inline). This service
 * also orchestrates the delete-organization saga: it starts/rolls-back the saga, while the actual
 * forward completion is driven by Kafka choreography ({@link OrganizationDeletionCompletionService}).
 */
@Service
@RequiredArgsConstructor
public class OrganizationCommandService {

    private final OrganizationRepository organizationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SponsorRepository sponsorRepository;
    private final OrganizationEventProducer organizationEventProducer;
    private final OrganizationDeletionCompletionService deletionCompletionService;

    @Transactional
    public Organization createOrganization(String name, String bankAccount,
                                           LocalDateTime bookingStartTime) {
        requireText(name, "name");
        requireText(bankAccount, "bankAccount");
        if (organizationRepository.existsByName(name)) {
            // IllegalStateException -> HTTP 409 (previously a bare RuntimeException -> 500).
            throw new IllegalStateException("Organization already exists: " + name);
        }
        Organization org = new Organization();
        org.setName(name);
        org.setBankAccount(bankAccount);
        org.setBookingStartTime(bookingStartTime);
        Organization saved = organizationRepository.save(org);

        OrganizationCreatedPayload payload = new OrganizationCreatedPayload(
                saved.getId(),
                saved.getName(),
                saved.getBankAccount(),
                saved.getBookingStartTime() != null ? saved.getBookingStartTime().toString() : null
        );
        organizationEventProducer.publishOrganizationCreated(payload);

        return saved;
    }

    @Transactional
    public Organization updateOrganization(UUID organizationId, String bankAccount,
                                           LocalDateTime bookingStartTime) {
        requireText(bankAccount, "bankAccount");
        Organization org = requireOrganization(organizationId);
        org.setBankAccount(bankAccount);
        org.setBookingStartTime(bookingStartTime);
        return organizationRepository.save(org);
    }

    @Transactional
    public TeamMember addTeamMember(UUID organizationId, UUID userId, String firstName,
                                    String lastName, String email, TeamMemberRole role) {
        requireText(firstName, "firstName");
        requireText(lastName, "lastName");
        requireText(email, "email");
        Organization org = requireOrganization(organizationId);

        TeamMember member = new TeamMember();
        member.setOrganization(org);
        member.setUserId(userId);
        member.setFirstName(firstName);
        member.setLastName(lastName);
        member.setEmail(email);
        member.setRole(role);
        return teamMemberRepository.save(member);
    }

    @Transactional
    public void removeTeamMember(UUID teamMemberId) {
        if (!teamMemberRepository.existsById(teamMemberId)) {
            throw new NotFoundException("Team member not found: " + teamMemberId);
        }
        teamMemberRepository.deleteById(teamMemberId);
    }

    @Transactional
    public Sponsor addSponsor(UUID organizationId, String name, BigDecimal amount) {
        requireText(name, "name");
        if (amount != null && amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        Organization org = requireOrganization(organizationId);

        Sponsor sponsor = new Sponsor();
        sponsor.setOrganization(org);
        sponsor.setName(name);
        sponsor.setAmount(amount);
        return sponsorRepository.save(sponsor);
    }

    @Transactional
    public void removeSponsor(UUID sponsorId) {
        if (!sponsorRepository.existsById(sponsorId)) {
            throw new NotFoundException("Sponsor not found: " + sponsorId);
        }
        sponsorRepository.deleteById(sponsorId);
    }

    /**
     * Starts the delete-organization saga: moves the org to {@code DELETING}, records
     * {@code OrganizationDeletionStarted} in the outbox, and arms the fallback completion timer so the
     * saga finishes even when the org has no active terms (and thus produces no BookingCancelled
     * events). The forward cascade (cancel terms → cancel bookings → refund payments) runs as Kafka
     * choreography; {@link OrganizationDeletionCompletionService} finalizes once activity settles.
     *
     * @throws NotFoundException     if the organization does not exist
     * @throws IllegalStateException if the organization is not {@code ACTIVE} (already being deleted)
     */
    @Transactional
    public void deleteOrganization(UUID organizationId) {
        Organization org = requireOrganization(organizationId);

        if (org.getStatus() != OrganizationStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Organization " + organizationId + " is already being deleted (status " + org.getStatus() + ")");
        }

        org.setStatus(OrganizationStatus.DELETING);
        organizationRepository.save(org);

        organizationEventProducer.publishOrganizationDeletionStarted(
                new OrganizationDeletionStartedPayload(org.getId(), org.getName()));

        // Arm the fallback timer so the saga completes even without any BookingCancelled activity.
        deletionCompletionService.scheduleFallback(org.getId());
    }

    /**
     * Rolls back a deletion saga that has NOT yet crossed the refund pivot. Restores the org to
     * {@code ACTIVE}, cancels the completion timer, and records {@code OrganizationDeletionRolledBack}
     * so downstream services compensate their own steps.
     *
     * @throws NotFoundException     if the organization does not exist
     * @throws IllegalStateException if the org is not in {@code DELETING} state, or the refund pivot
     *                               has already been crossed (a PAID payment was refunded — money left
     *                               the system and cannot be un-refunded here)
     */
    @Transactional
    public void rollbackDeletion(UUID organizationId) {
        Organization org = requireOrganization(organizationId);

        if (org.getStatus() != OrganizationStatus.DELETING) {
            throw new IllegalStateException(
                    "Cannot roll back: organization " + organizationId
                            + " is not in DELETING state (current: " + org.getStatus() + ")");
        }

        if (deletionCompletionService.isPivotCrossed(organizationId)) {
            throw new IllegalStateException(
                    "Cannot roll back: at least one PAID payment has already been refunded for organization "
                            + organizationId + ". The saga has passed the refund pivot and must complete forward.");
        }

        // Cancel the pending completion timer before changing state, so a late timer cannot finalize
        // the org to DELETED after a successful rollback.
        deletionCompletionService.cancelPendingFinalization(organizationId);

        org.setStatus(OrganizationStatus.ACTIVE);
        organizationRepository.save(org);

        organizationEventProducer.publishOrganizationDeletionRolledBack(
                new OrganizationDeletionRolledBackPayload(org.getId(), org.getName()));
    }

    private Organization requireOrganization(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found: " + organizationId));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
