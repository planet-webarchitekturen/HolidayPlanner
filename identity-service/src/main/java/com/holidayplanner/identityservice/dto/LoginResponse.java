package com.holidayplanner.identityservice.dto;

import com.holidayplanner.shared.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private UUID id;
    private String email;
    private String phoneNumber;
    private UUID organizationId;
    private UserRole role;
    private String token;
    private String refreshToken;
    private String tokenType = "Bearer";

    public LoginResponse(UUID id, String email, String phoneNumber, UUID organizationId, UserRole role,
                         String token, String refreshToken) {
        this.id = id;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.organizationId = organizationId;
        this.role = role;
        this.token = token;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
    }
}
