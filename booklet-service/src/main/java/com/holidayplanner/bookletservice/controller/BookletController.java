package com.holidayplanner.bookletservice.controller;

import com.holidayplanner.bookletservice.exception.UpstreamServiceException;
import com.holidayplanner.bookletservice.service.BookletService;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
  @PreAuthorize("hasAnyRole('ORGANIZATION_OWNER', 'ADMIN')")
  public ResponseEntity<byte[]> generateOrganizationBooklet(
      @PathVariable("organizationId") UUID organizationId) throws IOException {

    byte[] pdf = bookletService.generateOrganizationBooklet(organizationId);

    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"booklet-" + organizationId + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }

  @GetMapping("/participant-list/{eventTermId}")
  @PreAuthorize("hasRole('SERVICE')")
  public ResponseEntity<byte[]> getParticipantList(@PathVariable("eventTermId") UUID eventTermId)
      throws IOException {
    byte[] pdf = bookletService.readParticipantListPdf(eventTermId);

    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"participants-" + eventTermId + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }

  @ExceptionHandler(UpstreamServiceException.class)
  public ResponseEntity<String> handleUpstreamServiceException(UpstreamServiceException e) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
  }
}
