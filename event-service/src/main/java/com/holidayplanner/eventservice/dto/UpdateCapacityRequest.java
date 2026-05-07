package com.holidayplanner.eventservice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateCapacityRequest {
    private int minParticipants;
    private int maxParticipants;
}
