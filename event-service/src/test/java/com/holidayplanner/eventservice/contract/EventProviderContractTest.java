package com.holidayplanner.eventservice.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.eventservice.client.BookingServiceClient;
import com.holidayplanner.eventservice.client.IdentityServiceClient;
import com.holidayplanner.eventservice.client.NotificationServiceClient;
import com.holidayplanner.eventservice.dto.CreateEventRequest;
import com.holidayplanner.eventservice.dto.CreateEventTermRequest;
import com.holidayplanner.eventservice.kafka.EventTermEventProducer;
import com.holidayplanner.shared.model.PaymentMethod;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider-side shape check: {@code GET /api/events/terms/{id}} JSON must stay compatible with
 * booking-service {@code EventTermDetailResponse} parsing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventProviderContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void getEventTerm_returnsExpectedFieldNames() throws Exception {
        when(bookingServiceClient.getConfirmedBookingCount(any())).thenReturn(0L);

        CreateEventRequest create = new CreateEventRequest();
        create.setOrganizationId(UUID.randomUUID());
        create.setEventOwnerId(UUID.randomUUID());
        create.setShortTitle("Tour");
        create.setDescription("Desc");
        create.setLocation("Bregenz");
        create.setMeetingPoint("Harbour");
        create.setPrice(BigDecimal.TEN);
        create.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        create.setMinimalAge(6);
        create.setMaximalAge(12);
        create.setPictureUrl(null);

        String createBody = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(createBody);
        UUID eventId = UUID.fromString(root.get("id").asText());

        CreateEventTermRequest termReq = new CreateEventTermRequest();
        termReq.setStartDateTime(LocalDateTime.now().plusDays(5));
        termReq.setEndDateTime(LocalDateTime.now().plusDays(5).plusHours(3));
        termReq.setMinParticipants(2);
        termReq.setMaxParticipants(8);

        String termBody = mockMvc.perform(post("/api/events/{eventId}/terms", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(termReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID termId = UUID.fromString(objectMapper.readTree(termBody).get("id").asText());

        mockMvc.perform(get("/api/events/terms/{id}", termId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.maxParticipants").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.eventName").exists())
                .andExpect(jsonPath("$.startDateTime").exists());
    }
}
