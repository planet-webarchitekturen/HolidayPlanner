package com.holidayplanner.shared.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMemberAddedPayload {
    private UUID userId;
    private UUID familyMemberId;
    private String familyMemberName;
}
