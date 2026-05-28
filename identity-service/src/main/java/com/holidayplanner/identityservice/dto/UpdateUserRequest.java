package com.holidayplanner.identityservice.dto;

import com.holidayplanner.shared.model.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request body for the general user update.
 *
 * Partial-update semantics: any field left null is left unchanged.
 * The {@code role} and {@code organizationId} fields are administrative and may
 * only be set by an ADMIN (enforced in {@code IdentitySecurityService.canUpdateUser}).
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateUserRequest {
    private String email;
    private String phoneNumber;
    private String password;
    private UserRole role;
    private UUID organizationId;
}
