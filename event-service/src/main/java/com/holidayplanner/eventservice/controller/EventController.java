package com.holidayplanner.eventservice.controller;

import com.holidayplanner.eventservice.command.CancellationActor;
import com.holidayplanner.eventservice.command.EventCommandService;
import com.holidayplanner.eventservice.command.EventTermCommandService;
import com.holidayplanner.eventservice.command.RemarkCommandService;
import com.holidayplanner.eventservice.dto.ChangeStatusRequest;
import com.holidayplanner.eventservice.dto.CreateEventRequest;
import com.holidayplanner.eventservice.dto.CreateEventTermRequest;
import com.holidayplanner.eventservice.dto.CreateRemarkRequest;
import com.holidayplanner.eventservice.dto.EventResponse;
import com.holidayplanner.eventservice.dto.EventTermResponse;
import com.holidayplanner.eventservice.dto.RemarkResponse;
import com.holidayplanner.eventservice.dto.SendMessageRequest;
import com.holidayplanner.eventservice.dto.UpdateCapacityRequest;
import com.holidayplanner.eventservice.dto.UpdateEventRequest;
import com.holidayplanner.eventservice.query.EventQueryService;
import com.holidayplanner.eventservice.query.EventTermQueryService;
import com.holidayplanner.eventservice.query.RemarkQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventCommandService eventCommandService;
    private final EventTermCommandService eventTermCommandService;
    private final RemarkCommandService remarkCommandService;
    private final EventQueryService eventQueryService;
    private final EventTermQueryService eventTermQueryService;
    private final RemarkQueryService remarkQueryService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("EventService is running!");
    }

    // --- Events (queries) ---

    @GetMapping
    public ResponseEntity<List<EventResponse>> getEventsByOrganizationQuery(
            @RequestParam("organizationId") UUID organizationId) {
        return ResponseEntity.ok(eventQueryService.getEventsByOrganization(organizationId));
    }

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<List<EventResponse>> getEventsByOrganizationPath(
            @PathVariable("organizationId") UUID organizationId) {
        return ResponseEntity.ok(eventQueryService.getEventsByOrganization(organizationId));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable("eventId") UUID eventId) {
        return ResponseEntity.ok(eventQueryService.getEvent(eventId));
    }

    // --- Events (commands) ---

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventCommandService.createEvent(request));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable("eventId") UUID eventId,
            @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(eventCommandService.updateEvent(eventId, request));
    }

    // --- Event terms (queries) ---

    @GetMapping("/terms/{eventTermId}")
    public ResponseEntity<EventTermResponse> getEventTerm(@PathVariable("eventTermId") UUID eventTermId) {
        return ResponseEntity.ok(eventTermQueryService.getEventTerm(eventTermId));
    }

    // --- Event terms (commands) ---

    @PostMapping("/{eventId}/terms")
    public ResponseEntity<EventTermResponse> createEventTerm(
            @PathVariable("eventId") UUID eventId,
            @RequestBody CreateEventTermRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventTermCommandService.createEventTerm(eventId, request));
    }

    @PatchMapping("/terms/{eventTermId}/status")
    public ResponseEntity<EventTermResponse> changeEventTermStatus(
            @PathVariable("eventTermId") UUID eventTermId,
            @RequestBody ChangeStatusRequest request) {
        return ResponseEntity.ok(eventTermCommandService.changeEventTermStatus(
                eventTermId, request.getNewStatus(), CancellationActor.EVENT_OWNER));
    }

    @PatchMapping("/terms/{eventTermId}/capacity")
    public ResponseEntity<EventTermResponse> updateEventTermCapacity(
            @PathVariable("eventTermId") UUID eventTermId,
            @RequestBody UpdateCapacityRequest request) {
        return ResponseEntity.ok(eventTermCommandService.updateEventTermCapacity(
                eventTermId, request.getMinParticipants(), request.getMaxParticipants()));
    }

    @PostMapping("/terms/{eventTermId}/caregivers/{caregiverId}")
    public ResponseEntity<EventTermResponse> assignCaregiver(
            @PathVariable("eventTermId") UUID eventTermId,
            @PathVariable("caregiverId") UUID caregiverId) {
        return ResponseEntity.ok(eventTermCommandService.assignCaregiverToEventTerm(eventTermId, caregiverId));
    }

    @PostMapping("/terms/{eventTermId}/messages")
    public ResponseEntity<Void> sendMessageToParticipants(
            @PathVariable("eventTermId") UUID eventTermId,
            @RequestBody SendMessageRequest request) {
        eventTermCommandService.sendMessageToParticipants(
                eventTermId, request.getSubject(), request.getMessage());
        return ResponseEntity.noContent().build();
    }

    // --- Remarks ---

    @PostMapping("/terms/{eventTermId}/remarks")
    public ResponseEntity<RemarkResponse> createRemark(
            @PathVariable("eventTermId") UUID eventTermId,
            @RequestBody CreateRemarkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(remarkCommandService.createRemark(eventTermId, request));
    }

    @GetMapping("/terms/{eventTermId}/remarks")
    public ResponseEntity<List<RemarkResponse>> getRemarks(@PathVariable("eventTermId") UUID eventTermId) {
        return ResponseEntity.ok(remarkQueryService.getRemarks(eventTermId));
    }
}
