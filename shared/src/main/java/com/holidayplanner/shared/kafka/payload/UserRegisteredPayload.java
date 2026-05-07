package com.holidayplanner.shared.kafka.payload;

import com.holidayplanner.shared.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredPayload {
    private UUID userId;
    private String email;
    private String phoneNumber;
    private UUID organizationId;
    private UserRole role;
}
