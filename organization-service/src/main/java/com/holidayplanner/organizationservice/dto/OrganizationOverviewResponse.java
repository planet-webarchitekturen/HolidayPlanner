package com.holidayplanner.organizationservice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class OrganizationOverviewResponse {
    private OrganizationResponse organization;
    private List<EnrichedTeamMemberResponse> teamMembers;
    private List<SponsorResponse> sponsors;
    private int teamMemberCount;
    private int sponsorCount;
    private BigDecimal totalSponsorAmount;

    public static OrganizationOverviewResponse of(OrganizationResponse organization,
                                                  List<EnrichedTeamMemberResponse> teamMembers,
                                                  List<SponsorResponse> sponsors,
                                                  BigDecimal totalSponsorAmount) {
        OrganizationOverviewResponse response = new OrganizationOverviewResponse();
        response.organization = organization;
        response.teamMembers = teamMembers;
        response.sponsors = sponsors;
        response.teamMemberCount = teamMembers.size();
        response.sponsorCount = sponsors.size();
        response.totalSponsorAmount = totalSponsorAmount;
        return response;
    }
}
