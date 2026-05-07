package com.holidayplanner.bookletrequestservice.controller;

import com.holidayplanner.bookletrequestservice.command.BookletRequestCommandService;
import com.holidayplanner.bookletrequestservice.dto.BookletRequestCommandResponse;
import com.holidayplanner.bookletrequestservice.dto.BookletRequestDetailResponse;
import com.holidayplanner.bookletrequestservice.dto.BookletRequestSummaryResponse;
import com.holidayplanner.bookletrequestservice.dto.CreateBookletRequestCommand;
import com.holidayplanner.bookletrequestservice.exception.BookletRequestNotFoundException;
import com.holidayplanner.bookletrequestservice.exception.InvalidBookletRequestTransitionException;
import com.holidayplanner.bookletrequestservice.query.BookletRequestQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/booklet-requests")
@RequiredArgsConstructor
public class BookletRequestController {

    private final BookletRequestCommandService commandService;
    private final BookletRequestQueryService queryService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("BookletRequestService is running!");
    }

    @PostMapping
    public ResponseEntity<BookletRequestCommandResponse> create(
            @Valid @RequestBody CreateBookletRequestCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.create(command));
    }

    @PostMapping("/{id}/mark-printed")
    public ResponseEntity<BookletRequestCommandResponse> markPrinted(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(commandService.markPrinted(id));
    }

    @PostMapping("/{id}/mark-distributed")
    public ResponseEntity<BookletRequestCommandResponse> markDistributed(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(commandService.markDistributed(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BookletRequestCommandResponse> cancel(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(commandService.cancel(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookletRequestDetailResponse> getRequest(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(queryService.getRequest(id));
    }

    @GetMapping
    public ResponseEntity<List<BookletRequestDetailResponse>> getRequestsByOrganization(
            @RequestParam("organizationId") UUID organizationId) {
        return ResponseEntity.ok(queryService.getRequestsByOrganization(organizationId));
    }

    @GetMapping("/summary")
    public ResponseEntity<BookletRequestSummaryResponse> getSummary(
            @RequestParam("organizationId") UUID organizationId) {
        return ResponseEntity.ok(queryService.getSummary(organizationId));
    }

    @ExceptionHandler(BookletRequestNotFoundException.class)
    public ResponseEntity<String> handleNotFound(BookletRequestNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(InvalidBookletRequestTransitionException.class)
    public ResponseEntity<String> handleConflict(InvalidBookletRequestTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}
