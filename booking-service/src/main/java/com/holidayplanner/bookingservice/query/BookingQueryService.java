package com.holidayplanner.bookingservice.query;

import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.client.IdentityServiceClient;
import com.holidayplanner.bookingservice.dto.BookingDetailResponse;
import com.holidayplanner.bookingservice.dto.BookingResponse;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.dto.EventTermSummaryResponse;
import com.holidayplanner.bookingservice.repository.BookingRepository;
import com.holidayplanner.shared.model.Booking;
import com.holidayplanner.shared.model.BookingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingQueryService {

    private final BookingRepository bookingRepository;
    private final EventServiceClient eventServiceClient;
    private final IdentityServiceClient identityServiceClient;

    public List<BookingResponse> getBookingsForEventTerm(UUID eventTermId) {
        return bookingRepository.findByEventTermId(eventTermId).stream()
                .map(BookingResponse::from)
                .collect(Collectors.toList());
    }

    public long getBookingCount(UUID eventTermId) {
        return bookingRepository.countByEventTermIdAndStatus(eventTermId, BookingStatus.CONFIRMED);
    }

    public BookingResponse getBookingById(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(() -> new com.holidayplanner.bookingservice.exception.BookingNotFoundException(bookingId));
    }

    public List<BookingResponse> getBookingsForFamilyMember(UUID familyMemberId) {
        return bookingRepository.findByFamilyMemberId(familyMemberId).stream()
                .map(BookingResponse::from)
                .collect(Collectors.toList());
    }

    /** True if the member has any active (non-CANCELLED) booking — used by the identity removal veto. */
    public boolean hasActiveBookings(UUID familyMemberId) {
        return !bookingRepository.findActiveBookingsByFamilyMember(familyMemberId).isEmpty();
    }

    public List<BookingDetailResponse> getBookingsForFamilyMemberEnriched(UUID familyMemberId) {
        List<Booking> bookings = bookingRepository.findByFamilyMemberId(familyMemberId);
        return bookings.stream().map(booking -> {
            try {
                EventTermDetailResponse term = eventServiceClient.getEventTerm(booking.getEventTermId());
                return BookingDetailResponse.from(booking, term);
            } catch (Exception e) {
                log.warn("Could not fetch event details for booking {}", booking.getId());
                return BookingDetailResponse.fromBookingOnly(booking);
            }
        }).collect(Collectors.toList());
    }

    public EventTermSummaryResponse getEventTermSummary(UUID eventTermId) {
        long confirmedCount = bookingRepository.countByEventTermIdAndStatus(eventTermId, BookingStatus.CONFIRMED);
        long waitlistedCount = bookingRepository.countByEventTermIdAndStatus(eventTermId, BookingStatus.WAITLISTED);

        EventTermSummaryResponse summary = new EventTermSummaryResponse();
        summary.setEventTermId(eventTermId);
        summary.setConfirmedCount(confirmedCount);
        summary.setWaitlistedCount(waitlistedCount);

        try {
            EventTermDetailResponse term = eventServiceClient.getEventTerm(eventTermId);
            summary.setEventName(term.getEventName());
            summary.setTermStart(term.getStartDateTime());
            summary.setMaxParticipants(term.getMaxParticipants());
            long available = Math.max(0, term.getMaxParticipants() - confirmedCount);
            summary.setAvailableSpots(available);
            summary.setFull(available == 0);
        } catch (Exception e) {
            log.warn("Could not fetch event term details for summary of eventTermId {}", eventTermId);
            summary.setAvailableSpots(0);
            summary.setFull(false);
        }

        return summary;
    }

    public List<String> getParticipantParentEmails(UUID eventTermId) {
        List<Booking> a = bookingRepository.findByEventTermIdAndStatus(eventTermId, BookingStatus.CONFIRMED);
        return bookingRepository.findByEventTermIdAndStatus(eventTermId, BookingStatus.CONFIRMED).stream()
                .map(b -> identityServiceClient.getOwnerEmail(b.getFamilyMemberId()))
                .filter(email -> email != null)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getParticipantDisplayNames(UUID eventTermId) {
        return bookingRepository.findByEventTermIdAndStatus(eventTermId, BookingStatus.CONFIRMED).stream()
                .map(b -> identityServiceClient.getFamilyMemberDisplayName(b.getFamilyMemberId()))
                .filter(name -> name != null)
                .collect(Collectors.toList());
    }
}
