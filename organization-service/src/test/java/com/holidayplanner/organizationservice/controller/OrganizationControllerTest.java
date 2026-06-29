package com.holidayplanner.organizationservice.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.holidayplanner.organizationservice.command.OrganizationCommandService;
import com.holidayplanner.organizationservice.dto.OrganizationResponse;
import com.holidayplanner.organizationservice.query.OrganizationQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

    @Mock
    private OrganizationCommandService commandService;

    @Mock
    private OrganizationQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var objectMapper = Jackson2ObjectMapperBuilder.json()
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(new OrganizationController(commandService, queryService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getOrganizationReturnsBookingStartTimeForBookingWindowContract() throws Exception {
        UUID organizationId = UUID.randomUUID();

        OrganizationResponse response = new OrganizationResponse();
        response.setId(organizationId);
        response.setName("Holiday Helpers");
        response.setBankAccount("AT123");
        response.setBookingStartTime(LocalDateTime.of(2026, 6, 24, 10, 15, 30));
        when(queryService.getOrganization(organizationId)).thenReturn(response);

        mockMvc.perform(get("/api/organizations/{organizationId}", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(organizationId.toString()))
                .andExpect(jsonPath("$.bookingStartTime").value("2026-06-24T10:15:30"));
    }
}
