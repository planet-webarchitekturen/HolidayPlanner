package com.holidayplanner.identityservice.dto;

import com.holidayplanner.shared.model.FamilyMember;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * API view of a family member. Decouples the REST contract from the JPA entity
 * (e.g. exposes the owning userId as a flat field instead of the entity relation).
 */
@Getter
@Setter
@NoArgsConstructor
public class FamilyMemberResponse {
    private UUID id;
    private UUID userId;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String zip;

    public static FamilyMemberResponse from(FamilyMember member) {
        FamilyMemberResponse r = new FamilyMemberResponse();
        r.id = member.getId();
        r.userId = member.getUser() != null ? member.getUser().getId() : null;
        r.firstName = member.getFirstName();
        r.lastName = member.getLastName();
        r.birthDate = member.getBirthDate();
        r.zip = member.getZip();
        return r;
    }
}
