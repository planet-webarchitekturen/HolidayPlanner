package com.holidayplanner.bookingservice.controller;

import com.holidayplanner.bookingservice.command.BookingCommandService;
import com.holidayplanner.bookingservice.dto.BookingDetailResponse;
import com.holidayplanner.bookingservice.dto.BookingResponse;
import com.holidayplanner.bookingservice.dto.EventTermSummaryResponse;
import com.holidayplanner.bookingservice.query.BookingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingCommandService bookingCommandService;
    private final BookingQueryService bookingQueryService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("BookingService is running!");
    }

    @GetMapping("/event-term/{eventTermId}")
    public ResponseEntity<List<BookingResponse>> getBookingsForEventTerm(@PathVariable("eventTermId") UUID eventTermId) {
        return ResponseEntity.ok(bookingQueryService.getBookingsForEventTerm(eventTermId));
    }

    @GetMapping("/event-term/{eventTermId}/count")
    public ResponseEntity<Long> getBookingCount(@PathVariable("eventTermId") UUID eventTermId) {
        return ResponseEntity.ok(bookingQueryService.getBookingCount(eventTermId));
    }

    @GetMapping("/event-term/{eventTermId}/emails")
    public ResponseEntity<List<String>> getParticipantParentEmails(@PathVariable("eventTermId") UUID eventTermId) {
        try {
            return ResponseEntity.ok(bookingQueryService.getParticipantParentEmails(eventTermId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }

    }

    @GetMapping("/event-term/{eventTermId}/participant-names")
    public ResponseEntity<List<String>> getParticipantDisplayNames(@PathVariable("eventTermId") UUID eventTermId) {
        try
{
            return ResponseEntity.ok(bookingQueryService.getParticipantDisplayNames(eventTermId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }

    }

    @GetMapping("/event-term/{eventTermId}/summary")
    public ResponseEntity<EventTermSummaryResponse> getEventTermSummary(@PathVariable("eventTermId") UUID eventTermId) {
        return ResponseEntity.ok(bookingQueryService.getEventTermSummary(eventTermId));
    }

    @GetMapping("/family-member/{familyMemberId}")
    public ResponseEntity<List<BookingResponse>> getBookingsForFamilyMember(@PathVariable("familyMemberId") UUID familyMemberId) {
        return ResponseEntity.ok(bookingQueryService.getBookingsForFamilyMember(familyMemberId));
    }

    /** Veto support for identity-service: true if the member has any CONFIRMED/WAITLISTED booking. */
    @GetMapping("/family-member/{familyMemberId}/has-active")
    public ResponseEntity<Boolean> hasActiveBookings(@PathVariable("familyMemberId") UUID familyMemberId) {
        return ResponseEntity.ok(bookingQueryService.hasActiveBookings(familyMemberId));
    }

    @GetMapping("/family-member/{familyMemberId}/details")
    public ResponseEntity<List<BookingDetailResponse>> getBookingsForFamilyMemberEnriched(@PathVariable("familyMemberId") UUID familyMemberId) {
        return ResponseEntity.ok(bookingQueryService.getBookingsForFamilyMemberEnriched(familyMemberId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'EVENT_OWNER', 'ORGANIZATION_TEAM_MEMBER', 'ADMIN')")
    public ResponseEntity<BookingResponse> createBooking(
            @RequestParam("familyMemberId") UUID familyMemberId,
            @RequestParam("eventTermId") UUID eventTermId) {
        return ResponseEntity.ok(bookingCommandService.createBooking(familyMemberId, eventTermId));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable("bookingId") UUID bookingId) {
        return ResponseEntity.ok(bookingQueryService.getBookingById(bookingId));
    }

    @DeleteMapping("/{bookingId}")
    @PreAuthorize("hasAnyRole('USER', 'EVENT_OWNER', 'ORGANIZATION_TEAM_MEMBER', 'ADMIN')")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable("bookingId") UUID bookingId) {
        return ResponseEntity.ok(bookingCommandService.cancelBooking(bookingId));
    }
}
