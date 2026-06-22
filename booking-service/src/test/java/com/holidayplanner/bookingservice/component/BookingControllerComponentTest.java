package com.holidayplanner.bookingservice.component;

import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.exception.EventServiceException;
import com.holidayplanner.bookingservice.exception.EventTermNotFoundException;
import com.holidayplanner.bookingservice.repository.BookingRepository;
import com.holidayplanner.bookingservice.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Component (black-box) tests for booking-service.
 * The service is started in full; only the IPC dependency is mocked.
 * Tests go in through the HTTP layer and assert on HTTP responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingControllerComponentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @MockBean
    private EventServiceClient eventServiceClient;

    private static final UUID EVENT_TERM_ID = UUID.randomUUID();
    private static final UUID FAMILY_MEMBER_ID = UUID.randomUUID();
    private static final String USER_BEARER = "Bearer " + TestJwt.token("USER");

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
    }

    /** Performs the request with a USER JWT so it passes the security filter + @PreAuthorize checks. */
    private ResultActions send(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, USER_BEARER));
    }

    private EventTermDetailResponse activeEventTerm(int maxParticipants) {
        EventTermDetailResponse d = new EventTermDetailResponse();
        d.setId(EVENT_TERM_ID);
        d.setStatus("ACTIVE");
        d.setMaxParticipants(maxParticipants);
        return d;
    }

    // ── health ────────────────────────────────────────────────────────────────

    @Test
    void health_returns200() throws Exception {
        send(get("/api/bookings/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("BookingService is running!"));
    }

    // ── POST /api/bookings ────────────────────────────────────────────────────

    @Test
    void createBooking_returnsConfirmedBookingJson() throws Exception {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.familyMemberId").value(FAMILY_MEMBER_ID.toString()))
                .andExpect(jsonPath("$.eventTermId").value(EVENT_TERM_ID.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.bookedAt").isNotEmpty());
    }

    @Test
    void createBooking_whenFull_returnsWaitlistedBooking() throws Exception {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(0));

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITLISTED"));
    }

    @Test
    void createBooking_whenEventTermNotFound_returns404() throws Exception {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID))
                .thenThrow(new EventTermNotFoundException(EVENT_TERM_ID));

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Event term not found: " + EVENT_TERM_ID));
    }

    @Test
    void createBooking_whenEventTermNotActive_returns409() throws Exception {
        EventTermDetailResponse draft = new EventTermDetailResponse();
        draft.setStatus("DRAFT");
        draft.setMaxParticipants(10);
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(draft);

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createBooking_whenEventServiceDown_returns503() throws Exception {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID))
                .thenThrow(new EventServiceException("Event service unavailable",
                        new RuntimeException("Connection refused")));

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void createBooking_whenMissingParams_returns400() throws Exception {
        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString()))
                // eventTermId is missing → Spring returns 400
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBooking_whenInvalidUuid_returns400() throws Exception {
        send(post("/api/bookings")
                        .param("familyMemberId", "not-a-uuid")
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/bookings/{id} ─────────────────────────────────────────────

    @Test
    void cancelBooking_returns200WithCancelledStatus() throws Exception {
        when(eventServiceClient.getEventTerm(any())).thenReturn(activeEventTerm(10));

        String body = send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andReturn().getResponse().getContentAsString();

        // Extract id from response JSON (minimal parse)
        String bookingId = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        send(delete("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelBooking_whenNotFound_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID(); // never persisted → real H2 returns empty

        send(delete("/api/bookings/" + unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/bookings/event-term/{id} ────────────────────────────────────

    @Test
    void getBookingsForEventTerm_returnsArray() throws Exception {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        bookingService_createTwoBookings();

        send(get("/api/bookings/event-term/" + EVENT_TERM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getBookingsForEventTerm_whenNoneExist_returnsEmptyArray() throws Exception {
        send(get("/api/bookings/event-term/" + EVENT_TERM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /api/bookings/event-term/{id}/count ───────────────────────────────

    @Test
    void getBookingCount_returnsConfirmedCount() throws Exception {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        bookingService_createTwoBookings();

        send(get("/api/bookings/event-term/" + EVENT_TERM_ID + "/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));
    }

    private void bookingService_createTwoBookings() throws Exception {
        send(post("/api/bookings")
                .param("familyMemberId", UUID.randomUUID().toString())
                .param("eventTermId", EVENT_TERM_ID.toString()));
        send(post("/api/bookings")
                .param("familyMemberId", UUID.randomUUID().toString())
                .param("eventTermId", EVENT_TERM_ID.toString()));
    }
}
