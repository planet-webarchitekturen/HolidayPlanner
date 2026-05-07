package com.holidayplanner.bookingservice.controller;

import com.holidayplanner.bookingservice.command.BookingCommandService;
import com.holidayplanner.bookingservice.dto.BookingDetailResponse;
import com.holidayplanner.bookingservice.dto.BookingResponse;
import com.holidayplanner.bookingservice.dto.EventTermSummaryResponse;
import com.holidayplanner.bookingservice.query.BookingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/event-term/{eventTermId}/summary")
    public ResponseEntity<EventTermSummaryResponse> getEventTermSummary(@PathVariable("eventTermId") UUID eventTermId) {
        return ResponseEntity.ok(bookingQueryService.getEventTermSummary(eventTermId));
    }

    @GetMapping("/family-member/{familyMemberId}")
    public ResponseEntity<List<BookingResponse>> getBookingsForFamilyMember(@PathVariable("familyMemberId") UUID familyMemberId) {
        return ResponseEntity.ok(bookingQueryService.getBookingsForFamilyMember(familyMemberId));
    }

    @GetMapping("/family-member/{familyMemberId}/details")
    public ResponseEntity<List<BookingDetailResponse>> getBookingsForFamilyMemberEnriched(@PathVariable("familyMemberId") UUID familyMemberId) {
        return ResponseEntity.ok(bookingQueryService.getBookingsForFamilyMemberEnriched(familyMemberId));
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestParam("familyMemberId") UUID familyMemberId,
            @RequestParam("eventTermId") UUID eventTermId) {
        return ResponseEntity.ok(bookingCommandService.createBooking(familyMemberId, eventTermId));
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable("bookingId") UUID bookingId) {
        return ResponseEntity.ok(bookingCommandService.cancelBooking(bookingId));
    }
}
