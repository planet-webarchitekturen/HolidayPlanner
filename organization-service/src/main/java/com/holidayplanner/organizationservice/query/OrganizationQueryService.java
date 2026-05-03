package com.holidayplanner.organizationservice.query;

import com.holidayplanner.organizationservice.client.IdentityServiceClient;
import com.holidayplanner.organizationservice.dto.EnrichedTeamMemberResponse;
import com.holidayplanner.organizationservice.dto.OrganizationOverviewResponse;
import com.holidayplanner.organizationservice.dto.OrganizationResponse;
import com.holidayplanner.organizationservice.dto.SponsorResponse;
import com.holidayplanner.organizationservice.dto.TeamMemberResponse;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.organizationservice.repository.SponsorRepository;
import com.holidayplanner.organizationservice.repository.TeamMemberRepository;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.Sponsor;
import com.holidayplanner.shared.model.TeamMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationQueryService {

    private final OrganizationRepository organizationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SponsorRepository sponsorRepository;
    private final IdentityServiceClient identityServiceClient;

    public OrganizationResponse getOrganization(UUID organizationId) {
        return OrganizationResponse.from(findOrganization(organizationId));
    }

    public List<OrganizationResponse> getAllOrganizations() {
        return organizationRepository.findAll().stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    public List<TeamMemberResponse> getTeamMembers(UUID organizationId) {
        ensureOrganizationExists(organizationId);
        return teamMemberRepository.findByOrganization_Id(organizationId).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    public List<SponsorResponse> getSponsors(UUID organizationId) {
        ensureOrganizationExists(organizationId);
        return sponsorRepository.findByOrganization_Id(organizationId).stream()
                .map(SponsorResponse::from)
                .toList();
    }

    public OrganizationOverviewResponse getOrganizationOverview(UUID organizationId) {
        Organization organization = findOrganization(organizationId);
        List<TeamMember> teamMembers = teamMemberRepository.findByOrganization_Id(organizationId);
        List<Sponsor> sponsors = sponsorRepository.findByOrganization_Id(organizationId);

        List<EnrichedTeamMemberResponse> enrichedTeamMembers = teamMembers.stream()
                .map(member -> {
                    try {
                        return EnrichedTeamMemberResponse.from(
                                member,
                                identityServiceClient.getUser(member.getUserId())
                        );
                    } catch (Exception e) {
                        log.warn("Could not enrich team member {} with identity data", member.getId());
                        return EnrichedTeamMemberResponse.fromTeamMemberOnly(member);
                    }
                })
                .toList();

        BigDecimal totalSponsorAmount = sponsors.stream()
                .map(Sponsor::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrganizationOverviewResponse.of(
                OrganizationResponse.from(organization),
                enrichedTeamMembers,
                sponsors.stream().map(SponsorResponse::from).toList(),
                totalSponsorAmount
        );
    }

    public boolean organizationExists(UUID organizationId) {
        return organizationRepository.existsById(organizationId);
    }

    private Organization findOrganization(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));
    }

    private void ensureOrganizationExists(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new RuntimeException("Organization not found: " + organizationId);
        }
    }
}
