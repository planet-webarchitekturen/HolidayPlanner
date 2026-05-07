package com.holidayplanner.paymentservice.service;

import com.holidayplanner.paymentservice.command.PaymentCommandService;
import com.holidayplanner.paymentservice.dto.EventTermPaymentOverviewResponse;
import com.holidayplanner.paymentservice.query.PaymentQueryService;
import com.holidayplanner.shared.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Deprecated
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;

    public Payment createPayment(UUID bookingId, UUID organizationId, BigDecimal amount) {
        return paymentCommandService.createPayment(bookingId, organizationId, amount);
    }

    public Payment markAsPaid(UUID paymentId, String note) {
        return paymentCommandService.markAsPaid(paymentId, note);
    }

    public Payment refundPayment(UUID paymentId, String note) {
        return paymentCommandService.refundPayment(paymentId, note);
    }

    public List<Payment> getPaymentsByOrganization(UUID organizationId) {
        return paymentQueryService.getPaymentsByOrganization(organizationId);
    }

    public List<Payment> getPendingPayments(UUID organizationId) {
        return paymentQueryService.getPendingPayments(organizationId);
    }

    public Payment getPaymentByBooking(UUID bookingId) {
        return paymentQueryService.getPaymentByBooking(bookingId);
    }

    public BigDecimal calculateBalance(UUID organizationId) {
        return paymentQueryService.calculateBalance(organizationId);
    }

    public EventTermPaymentOverviewResponse getEventTermPaymentOverview(UUID eventTermId) {
        return paymentQueryService.getEventTermPaymentOverview(eventTermId);
    }
}
