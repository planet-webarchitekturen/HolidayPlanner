package com.holidayplanner.bookingservice.command;

import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.client.IdentityServiceClient;
import com.holidayplanner.bookingservice.dto.BookingResponse;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.exception.BookingNotFoundException;
import com.holidayplanner.bookingservice.exception.EventServiceException;
import com.holidayplanner.bookingservice.exception.EventTermNotFoundException;
import com.holidayplanner.bookingservice.kafka.BookingEventProducer;
import com.holidayplanner.bookingservice.repository.BookingRepository;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import com.holidayplanner.shared.model.Booking;
import com.holidayplanner.shared.model.BookingStatus;
import com.holidayplanner.shared.model.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the live CQRS {@link BookingCommandService} (replaces the deprecated BookingService
 * tests). All collaborators are mocked; no SecurityContext is set, so the org-scope guard is skipped
 * (current org id is null). Covers createBooking, cancelBooking and FIFO waitlist promotion.
 */
@ExtendWith(MockitoExtension.class)
class BookingCommandServiceUnitTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private EventServiceClient eventServiceClient;
    @Mock
    private IdentityServiceClient identityServiceClient;
    @Mock
    private BookingEventProducer bookingEventProducer;

    @InjectMocks
    private BookingCommandService bookingCommandService;

    private static final UUID FAMILY_MEMBER_ID = UUID.randomUUID();
    private static final UUID EVENT_TERM_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();

    private EventTermDetailResponse activeEventTerm(int maxParticipants) {
        EventTermDetailResponse d = new EventTermDetailResponse();
        d.setId(EVENT_TERM_ID);
        d.setStatus("ACTIVE");
        d.setMaxParticipants(maxParticipants);
        d.setEventName("Bike Adventure");
        d.setMeetingPoint("Main gate");
        d.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        d.setPrice(new BigDecimal("12.50"));
        return d;
    }

    private Booking booking(UUID id, UUID familyMemberId, BookingStatus status, LocalDateTime bookedAt) {
        Booking b = new Booking();
        b.setId(id);
        b.setFamilyMemberId(familyMemberId);
        b.setEventTermId(EVENT_TERM_ID);
        b.setStatus(status);
        b.setBookedAt(bookedAt);
        return b;
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBooking_whenSlotsAvailable_returnsConfirmed() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).thenReturn(5L);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));
        when(identityServiceClient.getOwnerEmail(FAMILY_MEMBER_ID)).thenReturn("parent@example.test");

        BookingResponse result = bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.getFamilyMemberId()).isEqualTo(FAMILY_MEMBER_ID);
        assertThat(result.getEventTermId()).isEqualTo(EVENT_TERM_ID);
        ArgumentCaptor<BookingCreatedPayload> payload = ArgumentCaptor.forClass(BookingCreatedPayload.class);
        verify(bookingEventProducer).publishBookingCreated(payload.capture());
        assertThat(payload.getValue().getMeetingPoint()).isEqualTo("Main gate");
        assertThat(payload.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(payload.getValue().getAmount()).isEqualByComparingTo("12.50");
    }

    @Test
    void createBooking_whenAtCapacity_returnsWaitlisted() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).thenReturn(10L);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));
        when(identityServiceClient.getOwnerEmail(FAMILY_MEMBER_ID)).thenReturn("parent@example.test");

        BookingResponse result = bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.WAITLISTED);
    }

    @Test
    void createBooking_whenExactlyOneSlotRemains_returnsConfirmed() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).thenReturn(9L);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));
        when(identityServiceClient.getOwnerEmail(FAMILY_MEMBER_ID)).thenReturn("parent@example.test");

        BookingResponse result = bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void createBooking_whenMaxZero_alwaysWaitlisted() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(0));
        when(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).thenReturn(0L);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));
        when(identityServiceClient.getOwnerEmail(FAMILY_MEMBER_ID)).thenReturn("parent@example.test");

        BookingResponse result = bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.WAITLISTED);
    }

    @Test
    void createBooking_whenEventTermNotActive_throwsAndNothingPersisted() {
        EventTermDetailResponse draft = new EventTermDetailResponse();
        draft.setStatus("DRAFT");
        draft.setMaxParticipants(10);
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(draft);

        assertThatThrownBy(() -> bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");

        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishBookingCreated(any());
    }

    @Test
    void createBooking_whenEventTermNotFound_propagatesAndNothingPersisted() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID))
                .thenThrow(new EventTermNotFoundException(EVENT_TERM_ID));

        assertThatThrownBy(() -> bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID))
                .isInstanceOf(EventTermNotFoundException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_whenEventServiceUnavailable_propagates() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID))
                .thenThrow(new EventServiceException("Event service unavailable",
                        new RuntimeException("Connection refused")));

        assertThatThrownBy(() -> bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID))
                .isInstanceOf(EventServiceException.class)
                .hasMessageContaining("unavailable");

        verify(bookingRepository, never()).save(any());
    }

    // ── cancelBooking ─────────────────────────────────────────────────────────

    @Test
    void cancelBooking_cancelsAndPromotesOldestWaitlisted() {
        Booking existing = booking(BOOKING_ID, FAMILY_MEMBER_ID, BookingStatus.CONFIRMED, LocalDateTime.now());
        Booking waitlisted = booking(UUID.randomUUID(), UUID.randomUUID(), BookingStatus.WAITLISTED,
                LocalDateTime.now());

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(existing));
        when(bookingRepository.findByEventTermIdAndStatusOrderByBookedAtAsc(EVENT_TERM_ID, BookingStatus.WAITLISTED))
                .thenReturn(List.of(waitlisted));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));

        BookingResponse result = bookingCommandService.cancelBooking(BOOKING_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(waitlisted.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository, times(2)).save(any()); // cancel + promote
    }

    @Test
    void cancelBooking_whenNoWaitlist_cancelsWithoutPromotion() {
        Booking existing = booking(BOOKING_ID, FAMILY_MEMBER_ID, BookingStatus.CONFIRMED, LocalDateTime.now());

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(existing));
        when(bookingRepository.findByEventTermIdAndStatusOrderByBookedAtAsc(EVENT_TERM_ID, BookingStatus.WAITLISTED))
                .thenReturn(List.of());
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));

        BookingResponse result = bookingCommandService.cancelBooking(BOOKING_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository, times(1)).save(any());
    }

    @Test
    void cancelBooking_whenNotFound_throws() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingCommandService.cancelBooking(BOOKING_ID))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(BOOKING_ID.toString());
    }

    // ── promoteFromWaitingList (FIFO) ─────────────────────────────────────────

    @Test
    void promoteFromWaitingList_promotesExactSlotsInFifoOrder() {
        // Repository returns oldest-first; service must promote the first N.
        Booking oldest = booking(UUID.randomUUID(), UUID.randomUUID(), BookingStatus.WAITLISTED,
                LocalDateTime.now().minusMinutes(30));
        Booking middle = booking(UUID.randomUUID(), UUID.randomUUID(), BookingStatus.WAITLISTED,
                LocalDateTime.now().minusMinutes(20));
        Booking newest = booking(UUID.randomUUID(), UUID.randomUUID(), BookingStatus.WAITLISTED,
                LocalDateTime.now().minusMinutes(10));

        when(bookingRepository.findByEventTermIdAndStatusOrderByBookedAtAsc(EVENT_TERM_ID, BookingStatus.WAITLISTED))
                .thenReturn(List.of(oldest, middle, newest));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));

        bookingCommandService.promoteFromWaitingList(EVENT_TERM_ID, 2);

        assertThat(oldest.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(middle.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(newest.getStatus()).isEqualTo(BookingStatus.WAITLISTED);
        verify(bookingRepository, times(2)).save(any());
        verify(bookingEventProducer, times(2)).publishWaitlistPromoted(any());
    }

    @Test
    void promoteFromWaitingList_whenEmpty_doesNothing() {
        when(bookingRepository.findByEventTermIdAndStatusOrderByBookedAtAsc(EVENT_TERM_ID, BookingStatus.WAITLISTED))
                .thenReturn(List.of());

        bookingCommandService.promoteFromWaitingList(EVENT_TERM_ID, 3);

        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishWaitlistPromoted(any());
    }
}
