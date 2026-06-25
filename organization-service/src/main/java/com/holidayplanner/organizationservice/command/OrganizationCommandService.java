package com.holidayplanner.organizationservice.command;

import com.holidayplanner.organizationservice.client.EventServiceClient;
import com.holidayplanner.organizationservice.kafka.OrganizationEventProducer;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.organizationservice.repository.SponsorRepository;
import com.holidayplanner.organizationservice.repository.TeamMemberRepository;
import com.holidayplanner.shared.kafka.payload.OrganizationCreatedPayload;
import com.holidayplanner.shared.kafka.payload.OrganizationDeletionStartedPayload;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import com.holidayplanner.shared.model.Sponsor;
import com.holidayplanner.shared.model.TeamMember;
import com.holidayplanner.shared.model.TeamMemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationCommandService {

    private final OrganizationRepository organizationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SponsorRepository sponsorRepository;
    private final OrganizationEventProducer organizationEventProducer;
    private final EventServiceClient eventServiceClient;

    public Organization createOrganization(String name, String bankAccount,
                                           LocalDateTime bookingStartTime) {
        if (organizationRepository.existsByName(name)) {
            throw new RuntimeException("Organization already exists: " + name);
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

    public Organization updateOrganization(UUID organizationId, String bankAccount,
                                           LocalDateTime bookingStartTime) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));
        org.setBankAccount(bankAccount);
        org.setBookingStartTime(bookingStartTime);
        return organizationRepository.save(org);
    }

    public TeamMember addTeamMember(UUID organizationId, UUID userId, String firstName,
                                    String lastName, String email, TeamMemberRole role) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        TeamMember member = new TeamMember();
        member.setOrganization(org);
        member.setUserId(userId);
        member.setFirstName(firstName);
        member.setLastName(lastName);
        member.setEmail(email);
        member.setRole(role);
        return teamMemberRepository.save(member);
    }

    public void removeTeamMember(UUID teamMemberId) {
        teamMemberRepository.deleteById(teamMemberId);
    }

    public Sponsor addSponsor(UUID organizationId, String name, BigDecimal amount) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        Sponsor sponsor = new Sponsor();
        sponsor.setOrganization(org);
        sponsor.setName(name);
        sponsor.setAmount(amount);
        return sponsorRepository.save(sponsor);
    }

    public void removeSponsor(UUID sponsorId) {
        sponsorRepository.deleteById(sponsorId);
    }

    public void deleteOrganization(UUID organizationId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        if (org.getStatus() != OrganizationStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Organization " + organizationId + " is already being deleted (status " + org.getStatus() + ")");
        }

        org.setStatus(OrganizationStatus.DELETING);
        organizationRepository.save(org);

        OrganizationDeletionStartedPayload payload = new OrganizationDeletionStartedPayload(
                org.getId(),
                org.getName()
        );
        organizationEventProducer.publishOrganizationDeletionStarted(payload);
    }
}