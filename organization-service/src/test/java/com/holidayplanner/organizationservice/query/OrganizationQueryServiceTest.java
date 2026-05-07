package com.holidayplanner.organizationservice.query;

import com.holidayplanner.organizationservice.client.IdentityServiceClient;
import com.holidayplanner.organizationservice.dto.IdentityUserResponse;
import com.holidayplanner.organizationservice.dto.OrganizationOverviewResponse;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.organizationservice.repository.SponsorRepository;
import com.holidayplanner.organizationservice.repository.TeamMemberRepository;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.Sponsor;
import com.holidayplanner.shared.model.TeamMember;
import com.holidayplanner.shared.model.TeamMemberRole;
import com.holidayplanner.shared.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationQueryServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private SponsorRepository sponsorRepository;

    @Mock
    private IdentityServiceClient identityServiceClient;

    @InjectMocks
    private OrganizationQueryService organizationQueryService;

    @Test
    void getOrganizationOverviewEnrichesTeamMembersAndAggregatesSponsors() {
        UUID organizationId = UUID.randomUUID();
        UUID teamMemberId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setName("Holiday Helpers");
        organization.setBankAccount("AT12");

        TeamMember member = new TeamMember();
        member.setId(teamMemberId);
        member.setOrganization(organization);
        member.setUserId(userId);
        member.setFirstName("Alex");
        member.setLastName("Planner");
        member.setEmail("alex@example.com");
        member.setRole(TeamMemberRole.ACCOUNTANT);

        Sponsor sponsor1 = new Sponsor();
        sponsor1.setId(UUID.randomUUID());
        sponsor1.setOrganization(organization);
        sponsor1.setName("Acme");
        sponsor1.setAmount(new BigDecimal("150.00"));

        Sponsor sponsor2 = new Sponsor();
        sponsor2.setId(UUID.randomUUID());
        sponsor2.setOrganization(organization);
        sponsor2.setName("Beta");
        sponsor2.setAmount(new BigDecimal("50.00"));

        IdentityUserResponse userResponse = new IdentityUserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("alex@example.com");
        userResponse.setPhoneNumber("+43123456");
        userResponse.setOrganizationId(organizationId);
        userResponse.setRole(UserRole.ADMIN);

        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
        when(teamMemberRepository.findByOrganization_Id(organizationId)).thenReturn(List.of(member));
        when(sponsorRepository.findByOrganization_Id(organizationId)).thenReturn(List.of(sponsor1, sponsor2));
        when(identityServiceClient.getUser(userId)).thenReturn(userResponse);

        OrganizationOverviewResponse overview = organizationQueryService.getOrganizationOverview(organizationId);

        assertEquals("Holiday Helpers", overview.getOrganization().getName());
        assertEquals(1, overview.getTeamMemberCount());
        assertEquals(2, overview.getSponsorCount());
        assertEquals(new BigDecimal("200.00"), overview.getTotalSponsorAmount());
        assertTrue(overview.getTeamMembers().getFirst().isIdentityDataAvailable());
        assertEquals("+43123456", overview.getTeamMembers().getFirst().getPhoneNumber());
        assertEquals(UserRole.ADMIN, overview.getTeamMembers().getFirst().getUserRole());
    }

    @Test
    void getOrganizationOverviewFallsBackWhenIdentityServiceFails() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setName("Holiday Helpers");

        TeamMember member = new TeamMember();
        member.setId(UUID.randomUUID());
        member.setOrganization(organization);
        member.setUserId(userId);
        member.setFirstName("Sam");
        member.setLastName("Fallback");
        member.setEmail("sam@example.com");
        member.setRole(TeamMemberRole.TEAM_MEMBER);

        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
        when(teamMemberRepository.findByOrganization_Id(organizationId)).thenReturn(List.of(member));
        when(sponsorRepository.findByOrganization_Id(organizationId)).thenReturn(List.of());
        when(identityServiceClient.getUser(userId)).thenThrow(new IllegalStateException("down"));

        OrganizationOverviewResponse overview = organizationQueryService.getOrganizationOverview(organizationId);

        assertEquals(1, overview.getTeamMemberCount());
        assertFalse(overview.getTeamMembers().getFirst().isIdentityDataAvailable());
        assertNull(overview.getTeamMembers().getFirst().getPhoneNumber());
        assertNull(overview.getTeamMembers().getFirst().getUserRole());
        assertEquals(BigDecimal.ZERO, overview.getTotalSponsorAmount());
    }
}
