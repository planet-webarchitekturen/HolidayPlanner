package com.holidayplanner.organizationservice.dto;

import com.holidayplanner.shared.model.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class IdentityUserResponse {
    private UUID id;
    private String email;
    private String phoneNumber;
    private UUID organizationId;
    private UserRole role;
}
