package com.holidayplanner.paymentservice.controller;

import com.holidayplanner.paymentservice.command.PaymentCommandService;
import com.holidayplanner.paymentservice.dto.EventTermPaymentOverviewResponse;
import com.holidayplanner.paymentservice.query.PaymentQueryService;
import com.holidayplanner.shared.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("PaymentService is running!");
    }

    @PostMapping
    public ResponseEntity<Payment> createPayment(
            @RequestParam("bookingId") UUID bookingId,
            @RequestParam("organizationId") UUID organizationId,
            @RequestParam("amount") BigDecimal amount) {
        return ResponseEntity.ok(paymentCommandService.createPayment(bookingId, organizationId, amount));
    }

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<List<Payment>> getPaymentsByOrganization(@PathVariable("organizationId") UUID organizationId) {
        return ResponseEntity.ok(paymentQueryService.getPaymentsByOrganization(organizationId));
    }

    @GetMapping("/organization/{organizationId}/pending")
    public ResponseEntity<List<Payment>> getPendingPayments(@PathVariable("organizationId") UUID organizationId) {
        return ResponseEntity.ok(paymentQueryService.getPendingPayments(organizationId));
    }

    @GetMapping("/event-terms/{eventTermId}/overview")
    public ResponseEntity<EventTermPaymentOverviewResponse> getEventTermPaymentOverview(
            @PathVariable("eventTermId") UUID eventTermId) {
        return ResponseEntity.ok(paymentQueryService.getEventTermPaymentOverview(eventTermId));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<Payment> getPaymentByBooking(@PathVariable("bookingId") UUID bookingId) {
        return ResponseEntity.ok(paymentQueryService.getPaymentByBooking(bookingId));
    }

    @PatchMapping("/{paymentId}/pay")
    public ResponseEntity<Payment> markAsPaid(
            @PathVariable("paymentId") UUID paymentId,
            @RequestParam(value = "note", required = false) String note) {
        return ResponseEntity.ok(paymentCommandService.markAsPaid(paymentId, note));
    }

    @PatchMapping("/{paymentId}/refund")
    public ResponseEntity<Payment> refundPayment(
            @PathVariable("paymentId") UUID paymentId,
            @RequestParam(value = "note", required = false) String note) {
        return ResponseEntity.ok(paymentCommandService.refundPayment(paymentId, note));
    }

    @GetMapping("/organization/{organizationId}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable("organizationId") UUID organizationId) {
        return ResponseEntity.ok(paymentQueryService.calculateBalance(organizationId));
    }
}
