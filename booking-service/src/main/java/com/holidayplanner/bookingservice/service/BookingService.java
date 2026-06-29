package com.holidayplanner.bookingservice.service;

import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.exception.BookingNotFoundException;
import com.holidayplanner.bookingservice.kafka.BookingEventProducer;
import com.holidayplanner.shared.kafka.payload.BookingCancelledPayload;
import com.holidayplanner.shared.kafka.payload.BookingCreatedPayload;
import com.holidayplanner.shared.kafka.payload.WaitlistPromotedPayload;
import com.holidayplanner.shared.model.Booking;
import com.holidayplanner.shared.model.BookingStatus;
import com.holidayplanner.shared.model.CancelledBy;
import com.holidayplanner.bookingservice.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Deprecated
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventServiceClient eventServiceClient;
    private final BookingEventProducer bookingEventProducer;

    public List<Booking> getBookingsForEventTerm(UUID eventTermId) {
        return bookingRepository.findByEventTermId(eventTermId);
    }

    public Booking createBooking(UUID familyMemberId, UUID eventTermId) {
        EventTermDetailResponse eventTerm = eventServiceClient.getEventTerm(eventTermId);

        if (!"ACTIVE".equals(eventTerm.getStatus())) {
            throw new IllegalStateException("Event term is not active: " + eventTermId);
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
            BookingCreatedPayload payload = new BookingCreatedPayload(
                    saved.getId(), saved.getFamilyMemberId(), saved.getEventTermId(),
                    status, null, null, null, null, null, null, null);
            bookingEventProducer.publishBookingCreated(payload);
        }

        return saved;
    }

    public Booking cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        BookingCancelledPayload payload = new BookingCancelledPayload(
                booking.getId(), booking.getFamilyMemberId(), booking.getEventTermId(),
                null, null, null, CancelledBy.USER,
                null, null);
        bookingEventProducer.publishBookingCancelled(payload);

        UUID eventTermId = booking.getEventTermId();
        if (eventTermId != null) {
            promoteFromWaitingList(eventTermId, 1);
        }

        return booking;
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

    public long getBookingCount(UUID eventTermId) {
        return bookingRepository.countByEventTermIdAndStatus(eventTermId, BookingStatus.CONFIRMED);
    }
}
