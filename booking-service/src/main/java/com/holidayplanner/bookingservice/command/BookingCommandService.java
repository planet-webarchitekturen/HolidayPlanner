package com.holidayplanner.bookingservice.command;

import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.client.IdentityServiceClient;
import com.holidayplanner.bookingservice.dto.BookingResponse;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.exception.BookingNotFoundException;
import com.holidayplanner.bookingservice.kafka.BookingEventProducer;
import com.holidayplanner.bookingservice.repository.BookingRepository;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import com.holidayplanner.shared.kafka.payload.WaitlistPromotedPayload;
import com.holidayplanner.shared.model.Booking;
import com.holidayplanner.shared.model.BookingStatus;
import com.holidayplanner.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCommandService {

    private final BookingRepository bookingRepository;
    private final EventServiceClient eventServiceClient;
    private final IdentityServiceClient identityServiceClient;
    private final BookingEventProducer bookingEventProducer;

    public BookingResponse createBooking(UUID familyMemberId, UUID eventTermId) {
        EventTermDetailResponse eventTerm = eventServiceClient.getEventTerm(eventTermId);

        if (!"ACTIVE".equals(eventTerm.getStatus())) {
            throw new IllegalStateException("Event term is not active: " + eventTermId);
        }

        // Cross-org check: only non-USER roles are org-scoped (parents/guardians can book any org's events)
        if (!SecurityUtils.hasRole("USER") && !SecurityUtils.hasRole("SERVICE")) {
            UUID currentOrgId = SecurityUtils.getCurrentOrganizationId();
            if (currentOrgId != null && eventTerm.getOrganizationId() != null
                    && !currentOrgId.equals(eventTerm.getOrganizationId())) {
                throw new AccessDeniedException("Event term belongs to a different organization");
            }
        }

        long confirmedCount = bookingRepository.countByEventTermIdAndStatus(eventTermId, BookingStatus.CONFIRMED);

        Booking booking = new Booking();
        booking.setFamilyMemberId(familyMemberId);
        booking.setEventTermId(eventTermId);
        BookingStatus status = confirmedCount < eventTerm.getMaxParticipants()
                ? BookingStatus.CONFIRMED
                : BookingStatus.WAITLISTED;
        booking.setStatus(status);

        Booking saved = bookingRepository.save(booking);

        if (status == BookingStatus.CONFIRMED) {
            String termDate = eventTerm.getStartDateTime() != null
                    ? eventTerm.getStartDateTime().toString() : null;
            String parentEmail = identityServiceClient.getOwnerEmail(familyMemberId);
            BookingCreatedPayload payload = new BookingCreatedPayload(
                    saved.getId(), saved.getFamilyMemberId(), saved.getEventTermId(),
                    status.name(), parentEmail, eventTerm.getEventName(), termDate,
                    eventTerm.getOrganizationId(), eventTerm.getPrice());
            bookingEventProducer.publishBookingCreated(payload);
        }

        return BookingResponse.from(saved);
    }

    public BookingResponse cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        String parentEmail = null;
        String eventName = null;
        String termDate = null;
        try {
            parentEmail = identityServiceClient.getOwnerEmail(booking.getFamilyMemberId());
            EventTermDetailResponse term = eventServiceClient.getEventTerm(booking.getEventTermId());
            eventName = term.getEventName();
            termDate = term.getStartDateTime() != null ? term.getStartDateTime().toString() : null;
        } catch (Exception e) {
            log.warn("Could not enrich BookingCancelledPayload for booking {}: {}", bookingId, e.getMessage());
        }
        BookingCancelledPayload payload = new BookingCancelledPayload(
                booking.getId(), booking.getFamilyMemberId(), booking.getEventTermId(),
                parentEmail, eventName, termDate, "parent");
        bookingEventProducer.publishBookingCancelled(payload);

        UUID eventTermId = booking.getEventTermId();
        if (eventTermId != null) {
            promoteFromWaitingList(eventTermId, 1);
        }

        return BookingResponse.from(booking);
    }

    public void cancelAllBookings(UUID eventTermId) {
        List<Booking> active = bookingRepository.findByEventTermId(eventTermId).stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .toList();
        active.forEach(b -> {
            b.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(b);
        });
    }

    public void promoteFromWaitingList(UUID eventTermId, int slots) {
        List<Booking> waitlisted = bookingRepository
                .findByEventTermIdAndStatus(eventTermId, BookingStatus.WAITLISTED);

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
                        log.warn("Could not fetch parentEmail for WaitlistPromotedPayload booking {}: {}", b.getId(), e.getMessage());
                    }
                    WaitlistPromotedPayload payload = new WaitlistPromotedPayload(
                            b.getId(), b.getFamilyMemberId(), b.getEventTermId(),
                            parentEmail, resolvedEventName, resolvedTermDate);
                    bookingEventProducer.publishWaitlistPromoted(payload);
                });
    }
}
