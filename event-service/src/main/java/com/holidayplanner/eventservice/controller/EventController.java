package com.holidayplanner.eventservice.controller;

import com.holidayplanner.shared.model.*;
import com.holidayplanner.eventservice.dto.EventTermResponse;
import com.holidayplanner.eventservice.service.EventManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventManagementService eventManagementService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("EventService is running!");
    }

    @PostMapping
    public ResponseEntity<Event> createEvent(
            @RequestParam("organizationId") UUID organizationId,
            @RequestParam("eventOwnerId") UUID eventOwnerId,
            @RequestParam("shortTitle") String shortTitle,
            @RequestParam("description") String description,
            @RequestParam("location") String location,
            @RequestParam("minimalAge") int minimalAge,
            @RequestParam("maximalAge") int maximalAge) {
        return ResponseEntity.ok(eventManagementService.createEvent(
                organizationId, eventOwnerId, shortTitle, description, location, minimalAge, maximalAge));
    }

    @GetMapping("/terms/{eventTermId}")
    public ResponseEntity<EventTermResponse> getEventTerm(@PathVariable("eventTermId") UUID eventTermId) {
        EventTerm term = eventManagementService.verifyEventTerm(eventTermId);
        return ResponseEntity.ok(EventTermResponse.from(term));
    }

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<List<Event>> getEventsByOrganization(@PathVariable("organizationId") UUID organizationId) {
        return ResponseEntity.ok(eventManagementService.getEventsByOrganization(organizationId));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<Event> updateEvent(
            @PathVariable("eventId") UUID eventId,
            @RequestParam("shortTitle") String shortTitle,
            @RequestParam("description") String description,
            @RequestParam("location") String location,
            @RequestParam(value = "meetingPoint", required = false) String meetingPoint,
            @RequestParam(value = "price", required = false) BigDecimal price,
            @RequestParam(value = "paymentMethod", required = false) PaymentMethod paymentMethod,
            @RequestParam("minimalAge") int minimalAge,
            @RequestParam("maximalAge") int maximalAge,
            @RequestParam(value = "pictureUrl", required = false) String pictureUrl) {
        return ResponseEntity.ok(eventManagementService.updateEvent(
                eventId, shortTitle, description, location, meetingPoint,
                price, paymentMethod, minimalAge, maximalAge, pictureUrl));
    }

    @PostMapping("/{eventId}/terms")
    public ResponseEntity<EventTerm> createEventTerm(
            @PathVariable("eventId") UUID eventId,
            @RequestParam("startDateTime") LocalDateTime startDateTime,
            @RequestParam("endDateTime") LocalDateTime endDateTime,
            @RequestParam("minParticipants") int minParticipants,
            @RequestParam("maxParticipants") int maxParticipants) {
        return ResponseEntity.ok(eventManagementService.createEventTerm(
                eventId, startDateTime, endDateTime, minParticipants, maxParticipants));
    }

    @PatchMapping("/terms/{eventTermId}/status")
    public ResponseEntity<EventTerm> changeEventTermStatus(
            @PathVariable("eventTermId") UUID eventTermId,
            @RequestParam("newStatus") EventTermStatus newStatus) {
        return ResponseEntity.ok(eventManagementService.changeEventTermStatus(eventTermId, newStatus));
    }

    @PatchMapping("/terms/{eventTermId}/capacity")
    public ResponseEntity<EventTerm> updateEventTermCapacity(
            @PathVariable("eventTermId") UUID eventTermId,
            @RequestParam("minParticipants") int minParticipants,
            @RequestParam("maxParticipants") int maxParticipants) {
        return ResponseEntity.ok(eventManagementService.updateEventTermCapacity(
                eventTermId, minParticipants, maxParticipants));
    }

    @PostMapping("/terms/{eventTermId}/caregivers/{caregiverId}")
    public ResponseEntity<EventTerm> assignCaregiver(
            @PathVariable("eventTermId") UUID eventTermId,
            @PathVariable("caregiverId") UUID caregiverId) {
        return ResponseEntity.ok(eventManagementService.assignCaregiverToEventTerm(eventTermId, caregiverId));
    }

    @PostMapping("/terms/{eventTermId}/remarks")
    public ResponseEntity<Remark> createRemark(
            @PathVariable("eventTermId") UUID eventTermId,
            @RequestParam("familyMemberId") UUID familyMemberId,
            @RequestParam("eventOwnerId") UUID eventOwnerId,
            @RequestParam("description") String description) {
        return ResponseEntity.ok(eventManagementService.createRemark(
                eventTermId, familyMemberId, eventOwnerId, description));
    }

    @GetMapping("/terms/{eventTermId}/remarks")
    public ResponseEntity<List<Remark>> getRemarks(@PathVariable("eventTermId") UUID eventTermId) {
        return ResponseEntity.ok(eventManagementService.getRemarks(eventTermId));
    }
}
