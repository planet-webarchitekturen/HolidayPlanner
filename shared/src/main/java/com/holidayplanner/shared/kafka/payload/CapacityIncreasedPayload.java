package com.holidayplanner.shared.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapacityIncreasedPayload {
    private UUID eventTermId;
    private int addedSlots;
    private int newMax;
}
