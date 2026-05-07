package com.holidayplanner.identityservice.dto;

import com.holidayplanner.shared.model.FamilyMember;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for a family member enriched with booking information.
 * Used in composition queries to show parents their family members and their booking status.
 */
@Getter
@Setter
@NoArgsConstructor
public class FamilyMemberWithBookingsResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String zip;
    private long activeBookingCount;  // Number of non-cancelled bookings (CONFIRMED or WAITLISTED)

    public static FamilyMemberWithBookingsResponse from(FamilyMember member, long bookingCount) {
        FamilyMemberWithBookingsResponse r = new FamilyMemberWithBookingsResponse();
        r.id = member.getId();
        r.firstName = member.getFirstName();
        r.lastName = member.getLastName();
        r.birthDate = member.getBirthDate();
        r.zip = member.getZip();
        r.activeBookingCount = bookingCount;
        return r;
    }
}
