package com.holidayplanner.eventservice.dto;

import com.holidayplanner.shared.model.EventTermStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangeStatusRequest {
    private EventTermStatus newStatus;
}
