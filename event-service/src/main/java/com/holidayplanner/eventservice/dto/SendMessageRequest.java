package com.holidayplanner.eventservice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SendMessageRequest {
    /** Optional; defaults to event title prefix in command service */
    private String subject;
    private String message;
}
