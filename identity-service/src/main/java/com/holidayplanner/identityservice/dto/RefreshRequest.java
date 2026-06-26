package com.holidayplanner.identityservice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RefreshRequest {
    private String refreshToken;
}
