package com.holidayplanner.bookingservice.command;

import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.client.IdentityServiceClient;
import com.holidayplanner.bookingservice.client.OrganizationServiceClient;
import com.holidayplanner.bookingservice.dto.BookingResponse;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.dto.FamilyMemberResponse;
import com.holidayplanner.bookingservice.dto.OrganizationResponse;
import com.holidayplanner.bookingservice.exception.BookingNotFoundException;
import com.holidayplanner.bookingservice.exception.EventServiceException;
import com.holidayplanner.bookingservice.exception.EventTermNotFoundException;
import com.holidayplanner.bookingservice.kafka.BookingEventProducer;
import com.holidayplanner.bookingservice.repository.BookingRepository;
import com.holidayplanner.shared.kafka.payload.BookingRestoredPayload;
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
import java.time.LocalDate;
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
    private OrganizationServiceClient organizationServiceClient;
    @Mock
    private BookingEventProducer bookingEventProducer;

    @InjectMocks
    private BookingCommandService bookingCommandService;

    private static final UUID FAMILY_MEMBER_ID = UUID.randomUUID();
    private static final UUID EVENT_TERM_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    private EventTermDetailResponse activeEventTerm(int maxParticipants) {
        EventTermDetailResponse d = new EventTermDetailResponse();
        d.setId(EVENT_TERM_ID);
        d.setStatus("ACTIVE");
        d.setMaxParticipants(maxParticipants);
        d.setOrganizationId(ORGANIZATION_ID);
        d.setStartDateTime(LocalDateTime.now().plusDays(14));
        d.setMinimalAge(6);
        d.setMaximalAge(16);
        d.setEventName("Bike Adventure");
        d.setMeetingPoint("Main gate");
        d.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        d.setPrice(new BigDecimal("12.50"));
        return d;
    }

    private FamilyMemberResponse familyMemberYearsOld(int years) {
        FamilyMemberResponse r = new FamilyMemberResponse();
        r.setId(FAMILY_MEMBER_ID);
        r.setBirthDate(LocalDate.now().minusYears(years));
        return r;
    }

    private FamilyMemberResponse familyMemberAgeOnTerm(EventTermDetailResponse eventTerm, int years) {
        FamilyMemberResponse r = new FamilyMemberResponse();
        r.setId(FAMILY_MEMBER_ID);
        r.setBirthDate(eventTerm.getStartDateTime().toLocalDate().minusYears(years));
        return r;
    }

    private OrganizationResponse openOrganization() {
        OrganizationResponse r = new OrganizationResponse();
        r.setId(ORGANIZATION_ID);
        r.setBookingStartTime(LocalDateTime.now().minusDays(1));
        return r;
    }

    private void givenBookingValidationPasses() {
        when(bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID)).thenReturn(familyMemberYearsOld(10));
        when(organizationServiceClient.getOrganization(ORGANIZATION_ID)).thenReturn(openOrganization());
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
        givenBookingValidationPasses();
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
        givenBookingValidationPasses();
        when(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).thenReturn(10L);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));
        when(identityServiceClient.getOwnerEmail(FAMILY_MEMBER_ID)).thenReturn("parent@example.test");

        BookingResponse result = bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.WAITLISTED);
    }

    @Test
    void createBooking_whenExactlyOneSlotRemains_returnsConfirmed() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        givenBookingValidationPasses();
        when(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).thenReturn(9L);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));
        when(identityServiceClient.getOwnerEmail(FAMILY_MEMBER_ID)).thenReturn("parent@example.test");

        BookingResponse result = bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void createBooking_whenMaxZero_alwaysWaitlisted() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(0));
        givenBookingValidationPasses();
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
    void createBooking_whenFamilyMemberTooYoung_throwsBadRequestAndNothingPersistedOrPublished() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID)).thenReturn(familyMemberYearsOld(5));

        assertThatThrownBy(() -> bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("age requirements");

        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishBookingCreated(any());
    }

    @Test
    void createBooking_whenFamilyMemberExactlyMinimalAge_returnsConfirmed() {
        EventTermDetailResponse eventTerm = activeEventTerm(10);
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(eventTerm);
        when(bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID))
                .thenReturn(familyMemberAgeOnTerm(eventTerm, eventTerm.getMinimalAge()));
        when(organizationServiceClient.getOrganization(ORGANIZATION_ID)).thenReturn(openOrganization());
        when(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).thenReturn(0L);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));
        when(identityServiceClient.getOwnerEmail(FAMILY_MEMBER_ID)).thenReturn("parent@example.test");

        BookingResponse result = bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingEventProducer).publishBookingCreated(any());
    }

    @Test
    void createBooking_whenFamilyMemberExactlyMaximalAge_returnsConfirmed() {
        EventTermDetailResponse eventTerm = activeEventTerm(10);
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(eventTerm);
        when(bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID))
                .thenReturn(familyMemberAgeOnTerm(eventTerm, eventTerm.getMaximalAge()));
        when(organizationServiceClient.getOrganization(ORGANIZATION_ID)).thenReturn(openOrganization());
        when(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).thenReturn(0L);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));
        when(identityServiceClient.getOwnerEmail(FAMILY_MEMBER_ID)).thenReturn("parent@example.test");

        BookingResponse result = bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingEventProducer).publishBookingCreated(any());
    }

    @Test
    void createBooking_whenFamilyMemberTooOld_throwsBadRequestAndNothingPersistedOrPublished() {
        EventTermDetailResponse eventTerm = activeEventTerm(10);
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(eventTerm);
        when(bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID))
                .thenReturn(familyMemberAgeOnTerm(eventTerm, eventTerm.getMaximalAge() + 1));

        assertThatThrownBy(() -> bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("age requirements");

        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishBookingCreated(any());
    }

    @Test
    void createBooking_whenBookingWindowNotOpen_throwsConflictAndNothingPersistedOrPublished() {
        OrganizationResponse futureOrganization = new OrganizationResponse();
        futureOrganization.setId(ORGANIZATION_ID);
        futureOrganization.setBookingStartTime(LocalDateTime.now().plusDays(1));

        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID)).thenReturn(familyMemberYearsOld(10));
        when(organizationServiceClient.getOrganization(ORGANIZATION_ID)).thenReturn(futureOrganization);

        assertThatThrownBy(() -> bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not open");

        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishBookingCreated(any());
    }

    @Test
    void createBooking_whenBookingStartTimeIsMissing_treatsBookingWindowAsOpen() {
        OrganizationResponse organizationWithoutBookingWindow = new OrganizationResponse();
        organizationWithoutBookingWindow.setId(ORGANIZATION_ID);
        organizationWithoutBookingWindow.setBookingStartTime(null);

        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID)).thenReturn(familyMemberYearsOld(10));
        when(organizationServiceClient.getOrganization(ORGANIZATION_ID)).thenReturn(organizationWithoutBookingWindow);
        when(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).thenReturn(0L);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));
        when(identityServiceClient.getOwnerEmail(FAMILY_MEMBER_ID)).thenReturn("parent@example.test");

        BookingResponse result = bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingEventProducer).publishBookingCreated(any());
    }

    @Test
    void createBooking_whenActiveDuplicateExists_throwsConflictAndNothingPersistedOrPublished() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(
                FAMILY_MEMBER_ID,
                EVENT_TERM_ID,
                List.of(BookingStatus.CONFIRMED, BookingStatus.WAITLISTED))).thenReturn(true);

        assertThatThrownBy(() -> bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active booking");

        verify(identityServiceClient, never()).getFamilyMember(any());
        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishBookingCreated(any());
    }

    @Test
    void createBooking_whenOnlyCancelledBookingExists_allowsNewBooking() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(
                FAMILY_MEMBER_ID,
                EVENT_TERM_ID,
                List.of(BookingStatus.CONFIRMED, BookingStatus.WAITLISTED))).thenReturn(false);
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID)).thenReturn(familyMemberYearsOld(10));
        when(organizationServiceClient.getOrganization(ORGANIZATION_ID)).thenReturn(openOrganization());
        when(bookingRepository.countByEventTermIdAndStatus(EVENT_TERM_ID, BookingStatus.CONFIRMED)).thenReturn(0L);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));
        when(identityServiceClient.getOwnerEmail(FAMILY_MEMBER_ID)).thenReturn("parent@example.test");

        BookingResponse result = bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository).existsByFamilyMemberIdAndEventTermIdAndStatusIn(
                FAMILY_MEMBER_ID,
                EVENT_TERM_ID,
                List.of(BookingStatus.CONFIRMED, BookingStatus.WAITLISTED));
        verify(bookingEventProducer).publishBookingCreated(any());
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

    // ── createBooking integrity guards (Story 1) ──────────────────────────────

    @Test
    void createBooking_whenChildTooYoung_throws400AndNothingPersisted() {
        EventTermDetailResponse term = activeEventTerm(10);
        term.setMinimalAge(8);
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(term);
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID))
                .thenReturn(familyMemberAgeOnTerm(term, 5));

        assertThatThrownBy(() -> bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishBookingCreated(any());
    }

    @Test
    void createBooking_beforeBookingWindow_throwsConflictAndNothingPersisted() {
        UUID orgId = UUID.randomUUID();
        EventTermDetailResponse term = activeEventTerm(10);
        term.setOrganizationId(orgId);
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(term);
        when(identityServiceClient.getFamilyMember(FAMILY_MEMBER_ID))
                .thenReturn(familyMemberAgeOnTerm(term, 10));
        OrganizationResponse org = new OrganizationResponse();
        org.setBookingStartTime(LocalDateTime.now().plusDays(5));
        when(organizationServiceClient.getOrganization(orgId)).thenReturn(org);

        assertThatThrownBy(() -> bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishBookingCreated(any());
    }

    @Test
    void createBooking_duplicateMemberAndTerm_throwsConflictAndNothingPersisted() {
        when(eventServiceClient.getEventTerm(EVENT_TERM_ID)).thenReturn(activeEventTerm(10));
        when(bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(
                FAMILY_MEMBER_ID,
                EVENT_TERM_ID,
                List.of(BookingStatus.CONFIRMED, BookingStatus.WAITLISTED))).thenReturn(true);

        assertThatThrownBy(() -> bookingCommandService.createBooking(FAMILY_MEMBER_ID, EVENT_TERM_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishBookingCreated(any());
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

    // ── cancelAllBookings – saga stamp ────────────────────────────────────────

    @Test
    void cancelAllBookings_stampsSagaFieldsBeforeCancelling() {
        Booking confirmed = booking(UUID.randomUUID(), FAMILY_MEMBER_ID, BookingStatus.CONFIRMED,
                LocalDateTime.now());
        Booking waitlisted = booking(UUID.randomUUID(), FAMILY_MEMBER_ID, BookingStatus.WAITLISTED,
                LocalDateTime.now().minusMinutes(5));

        when(bookingRepository.findByEventTermId(EVENT_TERM_ID))
                .thenReturn(List.of(confirmed, waitlisted));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));

        bookingCommandService.cancelAllBookings(EVENT_TERM_ID);

        assertThat(confirmed.isSagaCancelled()).isTrue();
        assertThat(confirmed.getSagaCancelledOriginalStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        assertThat(waitlisted.isSagaCancelled()).isTrue();
        assertThat(waitlisted.getSagaCancelledOriginalStatus()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(waitlisted.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelAllBookings_alreadyCancelledBookingsAreSkipped() {
        Booking alreadyCancelled = booking(UUID.randomUUID(), FAMILY_MEMBER_ID, BookingStatus.CANCELLED,
                LocalDateTime.now());

        when(bookingRepository.findByEventTermId(EVENT_TERM_ID))
                .thenReturn(List.of(alreadyCancelled));

        bookingCommandService.cancelAllBookings(EVENT_TERM_ID);

        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishBookingCancelled(any());
    }

    // ── restoreAllBookingsForTerm – saga rollback ─────────────────────────────

    @Test
    void restoreAllBookingsForTerm_restoresEachBookingToOriginalStatus() {
        UUID orgId = UUID.randomUUID();
        Booking b1 = booking(UUID.randomUUID(), FAMILY_MEMBER_ID, BookingStatus.CANCELLED, LocalDateTime.now());
        b1.setSagaCancelled(true);
        b1.setSagaCancelledOriginalStatus(BookingStatus.CONFIRMED);

        Booking b2 = booking(UUID.randomUUID(), FAMILY_MEMBER_ID, BookingStatus.CANCELLED, LocalDateTime.now());
        b2.setSagaCancelled(true);
        b2.setSagaCancelledOriginalStatus(BookingStatus.WAITLISTED);

        when(bookingRepository.findByEventTermIdAndSagaCancelledTrue(EVENT_TERM_ID))
                .thenReturn(List.of(b1, b2));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));

        bookingCommandService.restoreAllBookingsForTerm(EVENT_TERM_ID, orgId);

        assertThat(b1.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b1.isSagaCancelled()).isFalse();
        assertThat(b1.getSagaCancelledOriginalStatus()).isNull();

        assertThat(b2.getStatus()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(b2.isSagaCancelled()).isFalse();
        assertThat(b2.getSagaCancelledOriginalStatus()).isNull();
    }

    @Test
    void restoreAllBookingsForTerm_publishesBookingRestoredPerBooking() {
        UUID orgId = UUID.randomUUID();
        Booking b = booking(UUID.randomUUID(), FAMILY_MEMBER_ID, BookingStatus.CANCELLED, LocalDateTime.now());
        b.setSagaCancelled(true);
        b.setSagaCancelledOriginalStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findByEventTermIdAndSagaCancelledTrue(EVENT_TERM_ID))
                .thenReturn(List.of(b));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Booking.class));

        bookingCommandService.restoreAllBookingsForTerm(EVENT_TERM_ID, orgId);

        verify(bookingEventProducer).publishBookingRestored(any(BookingRestoredPayload.class));
    }

    @Test
    void restoreAllBookingsForTerm_whenNoSagaBookings_doesNothing() {
        UUID orgId = UUID.randomUUID();
        when(bookingRepository.findByEventTermIdAndSagaCancelledTrue(EVENT_TERM_ID))
                .thenReturn(List.of());

        bookingCommandService.restoreAllBookingsForTerm(EVENT_TERM_ID, orgId);

        verify(bookingRepository, never()).save(any());
        verify(bookingEventProducer, never()).publishBookingRestored(any());
    }
}
