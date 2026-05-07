package com.holidayplanner.organizationservice.dto;

import com.holidayplanner.shared.model.TeamMember;
import com.holidayplanner.shared.model.TeamMemberRole;
import com.holidayplanner.shared.model.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class EnrichedTeamMemberResponse {
    private UUID id;
    private UUID organizationId;
    private UUID userId;
    private String firstName;
    private String lastName;
    private String email;
    private TeamMemberRole role;
    private String phoneNumber;
    private UserRole userRole;
    private boolean identityDataAvailable;

    public static EnrichedTeamMemberResponse from(TeamMember member, IdentityUserResponse user) {
        EnrichedTeamMemberResponse response = fromTeamMemberOnly(member);
        response.phoneNumber = user.getPhoneNumber();
        response.userRole = user.getRole();
        response.identityDataAvailable = true;
        return response;
    }

    public static EnrichedTeamMemberResponse fromTeamMemberOnly(TeamMember member) {
        EnrichedTeamMemberResponse response = new EnrichedTeamMemberResponse();
        response.id = member.getId();
        response.organizationId = member.getOrganization().getId();
        response.userId = member.getUserId();
        response.firstName = member.getFirstName();
        response.lastName = member.getLastName();
        response.email = member.getEmail();
        response.role = member.getRole();
        response.identityDataAvailable = false;
        return response;
    }
}
