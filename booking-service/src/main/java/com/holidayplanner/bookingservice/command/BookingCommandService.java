package com.holidayplanner.bookingservice.command;

import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.client.IdentityServiceClient;
import com.holidayplanner.bookingservice.client.OrganizationServiceClient;
import com.holidayplanner.bookingservice.dto.BookingResponse;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.dto.FamilyMemberResponse;
import com.holidayplanner.bookingservice.dto.OrganizationResponse;
import com.holidayplanner.bookingservice.exception.BookingNotFoundException;
import com.holidayplanner.bookingservice.kafka.BookingEventProducer;
import com.holidayplanner.bookingservice.repository.BookingRepository;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import com.holidayplanner.shared.kafka.payload.BookingRestoredPayload;
import com.holidayplanner.shared.kafka.payload.WaitlistPromotedPayload;
import com.holidayplanner.shared.model.Booking;
import com.holidayplanner.shared.model.BookingStatus;
import com.holidayplanner.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BookingCommandService {

    private final BookingRepository bookingRepository;
    private final EventServiceClient eventServiceClient;
    private final IdentityServiceClient identityServiceClient;
    private final OrganizationServiceClient organizationServiceClient;
    private final BookingEventProducer bookingEventProducer;

    private static final List<BookingStatus> ACTIVE_BOOKING_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.WAITLISTED);

    public BookingResponse createBooking(UUID familyMemberId, UUID eventTermId) {
        EventTermDetailResponse eventTerm = eventServiceClient.getEventTerm(eventTermId);

        if (!"ACTIVE".equals(eventTerm.getStatus())) {
            throw new IllegalStateException("Event term is not active: " + eventTermId);
        }

        // Cross-org check: only non-USER roles are org-scoped (parents/guardians can
        // book any org's events)
        if (!SecurityUtils.hasRole("USER") && !SecurityUtils.hasRole("SERVICE")) {
            UUID currentOrgId = SecurityUtils.getCurrentOrganizationId();
            if (currentOrgId != null && eventTerm.getOrganizationId() != null
                    && !currentOrgId.equals(eventTerm.getOrganizationId())) {
                throw new AccessDeniedException("Event term belongs to a different organization");
            }
        }

        if (bookingRepository.existsByFamilyMemberIdAndEventTermIdAndStatusIn(
                familyMemberId, eventTermId, ACTIVE_BOOKING_STATUSES)) {
            throw new IllegalStateException("Family member already has an active booking for this event term");
        }

        FamilyMemberResponse familyMember = identityServiceClient.getFamilyMember(familyMemberId);
        validateAge(familyMember, eventTerm);
        validateBookingWindow(eventTerm);

        long confirmedCount = bookingRepository.countByEventTermIdAndStatus(eventTermId, BookingStatus.CONFIRMED);

        Booking booking = new Booking();
        booking.setFamilyMemberId(familyMemberId);
        booking.setEventTermId(eventTermId);
        BookingStatus status = confirmedCount < eventTerm.getMaxParticipants()
                ? BookingStatus.CONFIRMED
                : BookingStatus.WAITLISTED;
        booking.setStatus(status);

        Booking saved = bookingRepository.save(booking);

        String termDate = eventTerm.getStartDateTime() != null
                ? eventTerm.getStartDateTime().toString()
                : null;
        String parentEmail = identityServiceClient.getOwnerEmail(familyMemberId);
        BookingCreatedPayload payload = new BookingCreatedPayload(
                saved.getId(), saved.getFamilyMemberId(), saved.getEventTermId(),
                status.name(), parentEmail, eventTerm.getEventName(), termDate,
                eventTerm.getOrganizationId(), eventTerm.getPrice());
        bookingEventProducer.publishBookingCreated(payload);

        return BookingResponse.from(saved);
    }

    private void validateAge(FamilyMemberResponse familyMember, EventTermDetailResponse eventTerm) {
        if (familyMember == null || familyMember.getBirthDate() == null) {
            throw new IllegalArgumentException("Family member birth date is required");
        }
        if (eventTerm.getMinimalAge() == null || eventTerm.getMaximalAge() == null) {
            throw new IllegalStateException("Event term age limits are missing");
        }

        LocalDate referenceDate = eventTerm.getStartDateTime() != null
                ? eventTerm.getStartDateTime().toLocalDate()
                : LocalDate.now();
        int age = Period.between(familyMember.getBirthDate(), referenceDate).getYears();
        if (age < eventTerm.getMinimalAge() || age > eventTerm.getMaximalAge()) {
            throw new IllegalArgumentException("Family member does not meet age requirements");
        }
    }

    private void validateBookingWindow(EventTermDetailResponse eventTerm) {
        if (eventTerm.getOrganizationId() == null) {
            throw new IllegalStateException("Event term organizationId is missing");
        }

        OrganizationResponse organization = organizationServiceClient.getOrganization(eventTerm.getOrganizationId());
        if (organization == null) {
            throw new IllegalStateException("Organization response is missing");
        }

        LocalDateTime bookingStartTime = organization.getBookingStartTime();
        if (bookingStartTime != null && LocalDateTime.now().isBefore(bookingStartTime)) {
            throw new IllegalStateException("Booking is not open yet for organization: " + eventTerm.getOrganizationId());
        }
    }

    public BookingResponse cancelBooking(UUID bookingId) {

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId)); // orElse Throw catches empty returns and
                                                                             // throws exeption therefore
                                                                             // Optional<Booking> is not needed

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        String parentEmail = null;
        String eventName = null;
        String termDate = null;
        UUID eventTermOrganizationId = null;
        UUID eventTermEventId = null;
        try {
            parentEmail = identityServiceClient.getOwnerEmail(booking.getFamilyMemberId());
            EventTermDetailResponse term = eventServiceClient.getEventTerm(booking.getEventTermId());
            eventName = term.getEventName();
            termDate = term.getStartDateTime() != null ? term.getStartDateTime().toString() : null;
            eventTermOrganizationId = term.getOrganizationId();
            eventTermEventId = term.getEventId();
        } catch (Exception e) {
            log.warn("Could not enrich BookingCancelledPayload for booking {}: {}", bookingId, e.getMessage());
        }
        BookingCancelledPayload payload = new BookingCancelledPayload(
            booking.getId(), booking.getFamilyMemberId(), booking.getEventTermId(),
            parentEmail, eventName, termDate, "parent",
            eventTermOrganizationId, eventTermEventId);
        
            bookingEventProducer.publishBookingCancelled(payload);

        UUID eventTermId = booking.getEventTermId();
        if (eventTermId != null) {
            promoteFromWaitingList(eventTermId, 1);
        }

        return BookingResponse.from(booking);
    }

    public void cancelAllBookings(UUID eventTermId) {
        String eventName = null;
        String termDate = null;
        UUID organizationId = null;
        UUID eventTermEventId = null;
        try {
            EventTermDetailResponse term = eventServiceClient.getEventTerm(eventTermId);
            eventName = term.getEventName();
            termDate = term.getStartDateTime() != null ? term.getStartDateTime().toString() : null;
            organizationId = term.getOrganizationId();
            eventTermEventId = term.getEventId();
        } catch (Exception e) {
            log.warn("Could not fetch event term for cancelAllBookings: {}", e.getMessage());
        }
        final String resolvedEventName = eventName;
        final String resolvedTermDate = termDate;
        final UUID resolvedOrganizationId = organizationId;
        final UUID resolvedEventTermEventId = eventTermEventId;

        List<Booking> active = bookingRepository.findByEventTermId(eventTermId).stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .toList();
        active.forEach(b -> {
            b.setSagaCancelled(true);
            b.setSagaCancelledOriginalStatus(b.getStatus());
            b.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(b);
            String parentEmail = null;
            try {
                parentEmail = identityServiceClient.getOwnerEmail(b.getFamilyMemberId());
            } catch (Exception e) {
                log.warn("Could not fetch parentEmail for cancelAllBookings booking {}: {}", b.getId(), e.getMessage());
            }
                BookingCancelledPayload payload = new BookingCancelledPayload(
                    b.getId(), b.getFamilyMemberId(), b.getEventTermId(),
                    parentEmail, resolvedEventName, resolvedTermDate, "term-cancelled",
                    resolvedOrganizationId, resolvedEventTermEventId);
            bookingEventProducer.publishBookingCancelled(payload);
        });
    }

    public void restoreAllBookingsForTerm(UUID eventTermId, UUID organizationId) {
        List<Booking> sagaBookings = bookingRepository.findByEventTermIdAndSagaCancelledTrue(eventTermId);
        sagaBookings.forEach(b -> {
            BookingStatus originalStatus = b.getSagaCancelledOriginalStatus();
            if (originalStatus == null) {
                originalStatus = BookingStatus.CONFIRMED;
            }
            b.setStatus(originalStatus);
            b.setSagaCancelled(false);
            b.setSagaCancelledOriginalStatus(null);
            bookingRepository.save(b);
            bookingEventProducer.publishBookingRestored(
                    new BookingRestoredPayload(b.getId(), eventTermId, organizationId));
            log.info("Restored booking {} to {} (org rollback)", b.getId(), originalStatus);
        });
    }

    public void promoteFromWaitingList(UUID eventTermId, int slots) {
        List<Booking> waitlisted = bookingRepository
                .findByEventTermIdAndStatusOrderByBookedAtAsc(eventTermId, BookingStatus.WAITLISTED);

        String eventName = null;
        String termDate = null;
        try {
            EventTermDetailResponse term = eventServiceClient.getEventTerm(eventTermId);
            eventName = term.getEventName();
            termDate = term.getStartDateTime() != null ? term.getStartDateTime().toString() : null;
        } catch (Exception e) {
            log.warn("Could not fetch event term for WaitlistPromotedPayload: {}", e.getMessage());
        }

        final String resolvedEventName = eventName;
        final String resolvedTermDate = termDate;

        waitlisted.stream()
                .limit(slots)
                .forEach(b -> {
                    b.setStatus(BookingStatus.CONFIRMED);
                    bookingRepository.save(b);
                    String parentEmail = null;
                    try {
                        parentEmail = identityServiceClient.getOwnerEmail(b.getFamilyMemberId());
                    } catch (Exception e) {
                        log.warn("Could not fetch parentEmail for WaitlistPromotedPayload booking {}: {}", b.getId(),
                                e.getMessage());
                    }
                    WaitlistPromotedPayload payload = new WaitlistPromotedPayload(
                            b.getId(), b.getFamilyMemberId(), b.getEventTermId(),
                            parentEmail, resolvedEventName, resolvedTermDate);
                    bookingEventProducer.publishWaitlistPromoted(payload);
                });
    }
}
