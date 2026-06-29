package com.holidayplanner.bookingservice.contract;

import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.client.IdentityServiceClient;
import com.holidayplanner.bookingservice.client.OrganizationServiceClient;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.dto.FamilyMemberResponse;
import com.holidayplanner.bookingservice.dto.OrganizationResponse;
import com.holidayplanner.bookingservice.exception.EventServiceException;
import com.holidayplanner.bookingservice.exception.EventTermNotFoundException;
import com.holidayplanner.bookingservice.repository.BookingRepository;
import com.holidayplanner.bookingservice.support.TestJwt;
import com.holidayplanner.shared.model.Booking;
import com.holidayplanner.shared.model.BookingStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * C) Provider-side contract tests.
 *
 * Verifies that this service (as a provider) fulfils the API contract that
 * consumers depend on: correct HTTP status codes, Content-Type, and the exact
 * JSON field names / value types in every response shape.
 *
 * A consumer that relies on "$.id", "$.status", "$.familyMemberId", etc.
 * will break if any of these change — this test catches that.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingProviderContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @MockBean
    private EventServiceClient eventServiceClient;

    @MockBean
    private IdentityServiceClient identityServiceClient;

    @MockBean
    private OrganizationServiceClient organizationServiceClient;

    private static final UUID FAMILY_MEMBER_ID = UUID.randomUUID();
    private static final UUID EVENT_TERM_ID     = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final String USER_BEARER = "Bearer " + TestJwt.token("USER");

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        when(identityServiceClient.getFamilyMember(any())).thenAnswer(inv ->
                familyMember(inv.getArgument(0, UUID.class)));
        when(organizationServiceClient.getOrganization(any())).thenReturn(openOrganization());
    }

    /** Performs the request with a USER JWT so it passes the security filter + @PreAuthorize checks. */
    private ResultActions send(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, USER_BEARER));
    }

    private EventTermDetailResponse activeEventTerm(int maxParticipants) {
        EventTermDetailResponse term = new EventTermDetailResponse();
        term.setStatus("ACTIVE");
        term.setMaxParticipants(maxParticipants);
        term.setOrganizationId(ORGANIZATION_ID);
        term.setStartDateTime(LocalDateTime.now().plusDays(14));
        term.setMinimalAge(6);
        term.setMaximalAge(16);
        return term;
    }

    private FamilyMemberResponse familyMember(UUID id) {
        FamilyMemberResponse r = new FamilyMemberResponse();
        r.setId(id);
        r.setBirthDate(LocalDate.now().minusYears(10));
        return r;
    }

    private OrganizationResponse openOrganization() {
        OrganizationResponse r = new OrganizationResponse();
        r.setId(ORGANIZATION_ID);
        r.setBookingStartTime(LocalDateTime.now().minusDays(1));
        return r;
    }

    // ── Contract: POST /api/bookings → 200 Booking ───────────────────────────

    @Test
    void contract_createBooking_responseShape() throws Exception {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                // Required fields that consumers rely on
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.familyMemberId").value(FAMILY_MEMBER_ID.toString()))
                .andExpect(jsonPath("$.eventTermId").value(EVENT_TERM_ID.toString()))
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.bookedAt").isString())
                // No extra envelope; response is the flat Booking object
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void contract_createBooking_statusIsConfirmedOrWaitlisted() throws Exception {
        when(eventServiceClient.getEventTerm(any())).thenReturn(activeEventTerm(10));

        // First booking must be CONFIRMED when capacity allows
        send(post("/api/bookings")
                        .param("familyMemberId", UUID.randomUUID().toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Fill capacity then the next must be WAITLISTED
        when(eventServiceClient.getEventTerm(any())).thenReturn(activeEventTerm(0));

        UUID other = UUID.randomUUID();
        send(post("/api/bookings")
                        .param("familyMemberId", UUID.randomUUID().toString())
                        .param("eventTermId", other.toString()))
                .andExpect(jsonPath("$.status").value("WAITLISTED"));
    }

    // ── Contract: DELETE /api/bookings/{id} → 200 Booking ───────────────────

    @Test
    void contract_cancelBooking_responseShape() throws Exception {
        when(eventServiceClient.getEventTerm(any())).thenReturn(activeEventTerm(10));

        String created = send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andReturn().getResponse().getContentAsString();

        String bookingId = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        send(delete("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ── Contract: error responses ─────────────────────────────────────────────

    @Test
    void contract_hasActiveBookings_responseShape() throws Exception {
        Booking booking = new Booking();
        booking.setFamilyMemberId(FAMILY_MEMBER_ID);
        booking.setEventTermId(EVENT_TERM_ID);
        booking.setStatus(BookingStatus.WAITLISTED);
        bookingRepository.save(booking);

        send(get("/api/bookings/family-member/" + FAMILY_MEMBER_ID + "/has-active"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.hasActiveBookings").value(true))
                .andExpect(jsonPath("$.activeBookingCount").value(1))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void contract_404ErrorShape_whenEventTermNotFound() throws Exception {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID))
                .thenThrow(new EventTermNotFoundException(EVENT_TERM_ID));

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void contract_503ErrorShape_whenEventServiceDown() throws Exception {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID))
                .thenThrow(new EventServiceException("Event service unavailable",
                        new RuntimeException("Connection refused")));

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void contract_404ErrorShape_whenBookingNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();

        send(delete("/api/bookings/" + unknown))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Booking not found: " + unknown))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void contract_409ErrorShape_whenEventTermNotActive() throws Exception {
        EventTermDetailResponse draft = new EventTermDetailResponse();
        draft.setStatus("DRAFT");
        draft.setMaxParticipants(10);
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(draft);

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString());
    }
}
