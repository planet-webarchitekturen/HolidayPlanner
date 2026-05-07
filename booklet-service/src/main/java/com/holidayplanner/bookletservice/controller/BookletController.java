package com.holidayplanner.bookletservice.controller;

import com.holidayplanner.bookletservice.exception.UpstreamServiceException;
import com.holidayplanner.bookletservice.service.BookletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/booklets")
@RequiredArgsConstructor
public class BookletController {

    private final BookletService bookletService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("BookletService is running!");
    }

    @GetMapping("/organizations/{organizationId}")
    public ResponseEntity<byte[]> generateComposedOrganizationBooklet(
            @PathVariable("organizationId") UUID organizationId) throws IOException {

        byte[] pdf = bookletService.generateOrganizationBooklet(organizationId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"booklet-" + organizationId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // Generate full organization booklet as PDF download
    @PostMapping("/organization")
    public ResponseEntity<byte[]> generateBooklet(
            @RequestParam("organizationName") String organizationName,
            @RequestParam("contactInfo") String contactInfo,
            @RequestBody GenerateBookletRequest request) throws IOException {

        byte[] pdf = bookletService.generateBooklet(
                organizationName, contactInfo,
                request.getEventSummaries(), request.getSponsorNames());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"booklet-" + organizationName + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // Generate participant list PDF for caregiver
    @PostMapping("/participant-list")
    public ResponseEntity<byte[]> generateParticipantList(
            @RequestParam("eventName") String eventName,
            @RequestParam("termDate") String termDate,
            @RequestBody List<String> participantNames) throws IOException {

        byte[] pdf = bookletService.generateParticipantListPdf(eventName, termDate, participantNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"participants-" + eventName + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // Inner request class
    @lombok.Data
    public static class GenerateBookletRequest {
        private List<String> eventSummaries;
        private List<String> sponsorNames;
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<String> handleUpstreamServiceException(UpstreamServiceException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
    }
}
