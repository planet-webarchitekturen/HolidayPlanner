package com.holidayplanner.bookingservice.integration;

import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.client.IdentityServiceClient;
import com.holidayplanner.bookingservice.command.BookingCommandService;
import com.holidayplanner.bookingservice.dto.BookingResponse;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.exception.BookingNotFoundException;
import com.holidayplanner.bookingservice.exception.EventServiceException;
import com.holidayplanner.bookingservice.exception.EventTermNotFoundException;
import com.holidayplanner.bookingservice.kafka.BookingEventProducer;
import com.holidayplanner.bookingservice.repository.BookingRepository;
import com.holidayplanner.shared.model.Booking;
import com.holidayplanner.shared.model.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the CQRS {@link BookingCommandService} with real H2 persistence
 * (via @ActiveProfiles("test")). The IPC dependencies (event-service, identity-service) and the
 * Kafka producer are replaced by @MockBean so the test does not need any running infrastructure.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingServiceIntegrationTest {

    @Autowired
    private BookingCommandService bookingCommandService;

    @Autowired
    private BookingRepository bookingRepository;

    @MockBean
    private EventServiceClient eventServiceClient;

    @MockBean
    private IdentityServiceClient identityServiceClient;

    @MockBean
    private BookingEventProducer bookingEventProducer;

    private static final UUID EVENT_TERM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
    }

    private EventTermDetailResponse activeEventTerm(int maxParticipants) {
        EventTermDetailResponse d = new EventTermDetailResponse();
        d.setId(EVENT_TERM_ID);
        d.setStatus("ACTIVE");
        d.setMaxParticipants(maxParticipants);
        return d;
    }

    // ── createBooking – full flow with real persistence ───────────────────────

    @Test
    void createBooking_persistsConfirmedBookingAndSetsBookedAt() {
        UUID familyMemberId = UUID.randomUUID();
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));

        BookingResponse result = bookingCommandService.createBooking(familyMemberId, EVENT_TERM_ID);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.getBookedAt()).isNotNull();  // @PrePersist fires with real JPA

        Booking persisted = bookingRepository.findById(Objects.requireNonNull(result.getId())).orElseThrow();
        assertThat(persisted.getFamilyMemberId()).isEqualTo(familyMemberId);
        assertThat(persisted.getEventTermId()).isEqualTo(EVENT_TERM_ID);
    }

    @Test
    void createBooking_whenCapacityFull_persistsAsWaitlisted() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(2));

        bookingCommandService.createBooking(UUID.randomUUID(), EVENT_TERM_ID);
        bookingCommandService.createBooking(UUID.randomUUID(), EVENT_TERM_ID);
        BookingResponse third = bookingCommandService.createBooking(UUID.randomUUID(), EVENT_TERM_ID);

        assertThat(third.getStatus()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).isEqualTo(2);
        assertThat(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.WAITLISTED)).isEqualTo(1);
    }

    // ── createBooking – IPC failure handling ─────────────────────────────────

    @Test
    void createBooking_whenEventTermNotFound_throwsAndNothingPersisted() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID))
                .thenThrow(new EventTermNotFoundException(EVENT_TERM_ID));

        assertThatThrownBy(() -> bookingCommandService.createBooking(UUID.randomUUID(), EVENT_TERM_ID))
                .isInstanceOf(EventTermNotFoundException.class);

        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void createBooking_whenEventServiceUnavailable_throwsAndNothingPersisted() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID))
                .thenThrow(new EventServiceException("Event service unavailable",
                        new RuntimeException("Connection refused")));

        assertThatThrownBy(() -> bookingCommandService.createBooking(UUID.randomUUID(), EVENT_TERM_ID))
                .isInstanceOf(EventServiceException.class)
                .hasMessageContaining("unavailable");

        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void createBooking_whenEventTermNotActive_throwsIllegalState() {
        EventTermDetailResponse draft = new EventTermDetailResponse();
        draft.setStatus("DRAFT");
        draft.setMaxParticipants(10);
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(draft);

        assertThatThrownBy(() -> bookingCommandService.createBooking(UUID.randomUUID(), EVENT_TERM_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void createBooking_whenEventServiceReturnsPartialResponse_stillPersists() {
        // EventService returns a response with only the required fields; optional fields are null.
        EventTermDetailResponse partial = new EventTermDetailResponse();
        partial.setStatus("ACTIVE");
        partial.setMaxParticipants(5);
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(partial);

        BookingResponse result = bookingCommandService.createBooking(UUID.randomUUID(), EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    // ── cancelBooking – real DB promotion ────────────────────────────────────

    @Test
    void cancelBooking_promotesFirstWaitlistedBooking() {
        when(eventServiceClient.getEventTerm(any())).thenReturn(activeEventTerm(1));

        BookingResponse confirmed = bookingCommandService.createBooking(UUID.randomUUID(), EVENT_TERM_ID);
        BookingResponse waitlisted = bookingCommandService.createBooking(UUID.randomUUID(), EVENT_TERM_ID);

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(waitlisted.getStatus()).isEqualTo(BookingStatus.WAITLISTED);

        bookingCommandService.cancelBooking(Objects.requireNonNull(confirmed.getId()));

        Booking promoted = bookingRepository.findById(Objects.requireNonNull(waitlisted.getId())).orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        Booking cancelled = bookingRepository.findById(Objects.requireNonNull(confirmed.getId())).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_whenBookingNotFound_throwsBookingNotFoundException() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> bookingCommandService.cancelBooking(unknownId))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }
}
