package com.holidayplanner.identityservice.dto;

import com.holidayplanner.shared.model.Caregiver;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * API view of a caregiver. Decouples the REST contract from the JPA entity.
 */
@Getter
@Setter
@NoArgsConstructor
public class CaregiverResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;

    public static CaregiverResponse from(Caregiver caregiver) {
        CaregiverResponse r = new CaregiverResponse();
        r.id = caregiver.getId();
        r.firstName = caregiver.getFirstName();
        r.lastName = caregiver.getLastName();
        r.email = caregiver.getEmail();
        r.phoneNumber = caregiver.getPhoneNumber();
        return r;
    }
}
