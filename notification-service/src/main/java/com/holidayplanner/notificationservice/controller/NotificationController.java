package com.holidayplanner.notificationservice.controller;

import com.holidayplanner.shared.model.EmailRequest;
import com.holidayplanner.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("NotificationService is running!");
    }

    // Send a single email
    @PostMapping("/email")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION_OWNER')")
    public ResponseEntity<String> sendEmail(@RequestBody EmailRequest request) {
        notificationService.sendEmail(request.getTo(), request.getSubject(), request.getBody());
        return ResponseEntity.ok("Email sent");
    }

    // Send bulk email
    @PostMapping("/email/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION_OWNER')")
    public ResponseEntity<String> sendBulkEmail(@RequestBody EmailRequest request) {
        notificationService.sendBulkEmail(request.getRecipients(), request.getSubject(), request.getBody());
        return ResponseEntity.ok("Bulk email sent to " + request.getRecipients().size() + " recipients");
    }

    // Notify booking confirmed
    @PostMapping("/booking-confirmed")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION_OWNER')")
    public ResponseEntity<String> notifyBookingConfirmed(
            @RequestParam("parentEmail") String parentEmail,
            @RequestParam("eventName") String eventName,
            @RequestParam("termDate") String termDate) {
        notificationService.notifyBookingConfirmed(parentEmail, eventName, termDate);
        return ResponseEntity.ok("Booking confirmation sent");
    }

    // Notify term cancelled
    @PostMapping("/term-cancelled")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION_OWNER')")
    public ResponseEntity<String> notifyTermCancelled(
            @RequestParam("parentEmail") String parentEmail,
            @RequestParam("eventName") String eventName,
            @RequestParam("termDate") String termDate) {
        notificationService.notifyTermCancelled(parentEmail, eventName, termDate);
        return ResponseEntity.ok("Cancellation notification sent");
    }

    // Notify booking cancelled by owner
    @PostMapping("/booking-cancelled-by-owner")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION_OWNER')")
    public ResponseEntity<String> notifyBookingCancelledByOwner(
            @RequestParam("parentEmail") String parentEmail,
            @RequestParam("eventName") String eventName,
            @RequestParam("termDate") String termDate) {
        notificationService.notifyBookingCancelledByOwner(parentEmail, eventName, termDate);
        return ResponseEntity.ok("Cancellation by owner notification sent");
    }

    // Notify caregiver with participant list
    @PostMapping("/caregiver-participants")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION_OWNER')")
    public ResponseEntity<String> notifyCaregiverWithParticipants(
            @RequestParam("caregiverEmail") String caregiverEmail,
            @RequestParam("eventName") String eventName,
            @RequestParam("termDate") String termDate,
            @RequestBody List<String> participantNames) {
        notificationService.notifyCaregiverWithParticipants(caregiverEmail, eventName, termDate, participantNames);
        return ResponseEntity.ok("Caregiver notified with participant list");
    }

    // Notify caregivers of auto-cancellation
    @PostMapping("/auto-cancellation")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION_OWNER')")
    public ResponseEntity<String> notifyAutoCancellation(
            @RequestParam("eventName") String eventName,
            @RequestParam("termDate") String termDate,
            @RequestBody List<String> caregiverEmails) {
        notificationService.notifyCaregiversOfAutoCancellation(caregiverEmails, eventName, termDate);
        return ResponseEntity.ok("Auto-cancellation notifications sent");
    }
}
