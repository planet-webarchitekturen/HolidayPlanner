package com.holidayplanner.bookingservice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class FamilyMemberResponse {
    private UUID id;
    private LocalDate birthDate;
}
