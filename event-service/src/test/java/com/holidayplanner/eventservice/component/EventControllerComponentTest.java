package com.holidayplanner.eventservice.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.eventservice.client.BookingServiceClient;
import com.holidayplanner.eventservice.client.IdentityServiceClient;
import com.holidayplanner.eventservice.client.NotificationServiceClient;
import com.holidayplanner.eventservice.dto.ChangeStatusRequest;
import com.holidayplanner.eventservice.dto.CreateEventRequest;
import com.holidayplanner.eventservice.dto.CreateEventTermRequest;
import com.holidayplanner.eventservice.kafka.EventTermEventProducer;
import com.holidayplanner.eventservice.repository.EventRepository;
import com.holidayplanner.eventservice.repository.EventTermRepository;
import com.holidayplanner.shared.model.EventTermStatus;
import com.holidayplanner.shared.model.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerComponentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventTermRepository eventTermRepository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private EventTermEventProducer eventTermEventProducer;

    @MockBean
    private BookingServiceClient bookingServiceClient;

    @MockBean
    private IdentityServiceClient identityServiceClient;

    @MockBean
    private NotificationServiceClient notificationServiceClient;

    @BeforeEach
    void clean() {
        eventTermRepository.deleteAll();
        eventRepository.deleteAll();
        when(bookingServiceClient.getConfirmedBookingCount(any())).thenReturn(0L);
    }

    @Test
    void health_returns200() throws Exception {
        mockMvc.perform(get("/api/events/health"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("EventService is running!"));
    }

    @Test
    void getEventTerm_whenMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/events/terms/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void changeStatus_invalidTransition_returns400() throws Exception {
        CreateEventRequest create = new CreateEventRequest();
        create.setOrganizationId(UUID.randomUUID());
        create.setEventOwnerId(UUID.randomUUID());
        create.setShortTitle("T");
        create.setDescription("D");
        create.setLocation("L");
        create.setMeetingPoint("M");
        create.setPrice(BigDecimal.ONE);
        create.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        create.setMinimalAge(1);
        create.setMaximalAge(10);
        String body = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID eventId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        CreateEventTermRequest termReq = new CreateEventTermRequest();
        termReq.setStartDateTime(LocalDateTime.now().plusDays(1));
        termReq.setEndDateTime(LocalDateTime.now().plusDays(1).plusHours(1));
        termReq.setMinParticipants(1);
        termReq.setMaxParticipants(5);
        String termBody = mockMvc.perform(post("/api/events/{eventId}/terms", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(termReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID termId = UUID.fromString(objectMapper.readTree(termBody).get("id").asText());

        ChangeStatusRequest toActive = new ChangeStatusRequest();
        toActive.setNewStatus(EventTermStatus.ACTIVE);
        mockMvc.perform(patch("/api/events/terms/{id}/status", termId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toActive)))
                .andExpect(status().isOk());

        ChangeStatusRequest toDraft = new ChangeStatusRequest();
        toDraft.setNewStatus(EventTermStatus.DRAFT);
        mockMvc.perform(patch("/api/events/terms/{id}/status", termId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toDraft)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEventsByOrganization_returnsArray() throws Exception {
        UUID org = UUID.randomUUID();
        mockMvc.perform(get("/api/events").param("organizationId", org.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
