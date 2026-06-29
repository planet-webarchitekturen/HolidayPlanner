package com.holidayplanner.bookingservice.component;

import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.client.IdentityServiceClient;
import com.holidayplanner.bookingservice.client.OrganizationServiceClient;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.dto.FamilyMemberResponse;
import com.holidayplanner.bookingservice.dto.OrganizationResponse;
import com.holidayplanner.bookingservice.exception.EventServiceException;
import com.holidayplanner.bookingservice.exception.EventTermNotFoundException;
import com.holidayplanner.bookingservice.outbox.OutboxEventRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private EventServiceClient eventServiceClient;

    @MockBean
    private IdentityServiceClient identityServiceClient;

    @MockBean
    private OrganizationServiceClient organizationServiceClient;

    private static final UUID EVENT_TERM_ID = UUID.randomUUID();
    private static final UUID FAMILY_MEMBER_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final String USER_BEARER = "Bearer " + TestJwt.token("USER");

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
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
        EventTermDetailResponse d = new EventTermDetailResponse();
        d.setId(EVENT_TERM_ID);
        d.setStatus("ACTIVE");
        d.setMaxParticipants(maxParticipants);
        d.setOrganizationId(ORGANIZATION_ID);
        d.setStartDateTime(LocalDateTime.now().plusDays(14));
        d.setMinimalAge(6);
        d.setMaximalAge(16);
        return d;
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

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo("BookingCreated");
                    assertThat(event.getTopic()).isEqualTo("holiday-planner.booking.created");
                    assertThat(event.isProcessed()).isFalse();
                });
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

    // Additional POST /api/bookings validation cases

    @Test
    void createBooking_whenFamilyMemberUnderage_returns400AndDoesNotPersist() throws Exception {
        FamilyMemberResponse underage = familyMember(FAMILY_MEMBER_ID);
        underage.setBirthDate(LocalDate.now().minusYears(5));

        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID)).thenReturn(underage);

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void createBooking_whenBookingWindowNotOpen_returns409AndDoesNotPersist() throws Exception {
        OrganizationResponse futureOrganization = openOrganization();
        futureOrganization.setBookingStartTime(LocalDateTime.now().plusDays(1));

        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(organizationServiceClient.getOrganization(ORGANIZATION_ID)).thenReturn(futureOrganization);

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void createBooking_whenDuplicateActiveBooking_returns409AndDoesNotPersistSecondBooking() throws Exception {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isOk());

        send(post("/api/bookings")
                        .param("familyMemberId", FAMILY_MEMBER_ID.toString())
                        .param("eventTermId", EVENT_TERM_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    // DELETE /api/bookings/{id}

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
    void hasActiveBookings_whenConfirmedBookingExists_returnsTrueWithCount() throws Exception {
        saveBooking(FAMILY_MEMBER_ID, BookingStatus.CONFIRMED);

        send(get("/api/bookings/family-member/" + FAMILY_MEMBER_ID + "/has-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveBookings").value(true))
                .andExpect(jsonPath("$.activeBookingCount").value(1));
    }

    @Test
    void hasActiveBookings_whenWaitlistedBookingExists_returnsTrueWithCount() throws Exception {
        saveBooking(FAMILY_MEMBER_ID, BookingStatus.WAITLISTED);

        send(get("/api/bookings/family-member/" + FAMILY_MEMBER_ID + "/has-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveBookings").value(true))
                .andExpect(jsonPath("$.activeBookingCount").value(1));
    }

    @Test
    void hasActiveBookings_whenOnlyCancelledBookingsExist_returnsFalseWithZeroCount() throws Exception {
        saveBooking(FAMILY_MEMBER_ID, BookingStatus.CANCELLED);

        send(get("/api/bookings/family-member/" + FAMILY_MEMBER_ID + "/has-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveBookings").value(false))
                .andExpect(jsonPath("$.activeBookingCount").value(0));
    }

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

    private void saveBooking(UUID familyMemberId, BookingStatus status) {
        Booking booking = new Booking();
        booking.setFamilyMemberId(familyMemberId);
        booking.setEventTermId(UUID.randomUUID());
        booking.setStatus(status);
        bookingRepository.save(booking);
    }
}
