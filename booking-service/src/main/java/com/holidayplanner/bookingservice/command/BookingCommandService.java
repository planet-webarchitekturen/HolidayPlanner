package com.holidayplanner.bookingservice.command;

import com.holidayplanner.bookingservice.client.EventServiceClient;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingCommandService {

    private final BookingRepository bookingRepository;
    private final EventServiceClient eventServiceClient;
    private final BookingEventProducer bookingEventProducer;

    public BookingResponse createBooking(UUID familyMemberId, UUID eventTermId) {
        EventTermDetailResponse eventTerm = eventServiceClient.getEventTerm(eventTermId);

        if (!"ACTIVE".equals(eventTerm.getStatus())) {
            throw new IllegalStateException("Event term is not active: " + eventTermId);
        }

        UUID currentOrgId = SecurityUtils.getCurrentOrganizationId();
        if (currentOrgId != null && eventTerm.getOrganizationId() != null
                && !currentOrgId.equals(eventTerm.getOrganizationId())) {
            throw new AccessDeniedException("Event term belongs to a different organization");
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
            BookingCreatedPayload payload = new BookingCreatedPayload(
                    saved.getId(), saved.getFamilyMemberId(), saved.getEventTermId(),
                    status.name(), null, eventTerm.getEventName(), termDate,
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

        BookingCancelledPayload payload = new BookingCancelledPayload(
                booking.getId(), booking.getFamilyMemberId(), booking.getEventTermId(),
                null, null, null, "parent");
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

        waitlisted.stream()
                .limit(slots)
                .forEach(b -> {
                    b.setStatus(BookingStatus.CONFIRMED);
                    bookingRepository.save(b);
                    WaitlistPromotedPayload payload = new WaitlistPromotedPayload(
                            b.getId(), b.getFamilyMemberId(), b.getEventTermId(),
                            null, null, null);
                    bookingEventProducer.publishWaitlistPromoted(payload);
                });
    }
}
