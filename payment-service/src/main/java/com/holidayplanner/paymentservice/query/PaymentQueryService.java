package com.holidayplanner.paymentservice.query;

import com.holidayplanner.paymentservice.client.BookingServiceClient;
import com.holidayplanner.paymentservice.client.EventServiceClient;
import com.holidayplanner.paymentservice.dto.BookingClientResponse;
import com.holidayplanner.paymentservice.dto.EventTermClientResponse;
import com.holidayplanner.paymentservice.dto.EventTermPaymentOverviewResponse;
import com.holidayplanner.paymentservice.dto.EventTermPaymentParticipantResponse;
import com.holidayplanner.paymentservice.repository.PaymentRepository;
import com.holidayplanner.shared.model.BookingStatus;
import com.holidayplanner.shared.model.Payment;
import com.holidayplanner.shared.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final BookingServiceClient bookingServiceClient;
    private final EventServiceClient eventServiceClient;

    public List<Payment> getPaymentsByOrganization(UUID organizationId) {
        return paymentRepository.findByOrganizationId(organizationId);
    }

    public List<Payment> getPendingPayments(UUID organizationId) {
        return paymentRepository.findByOrganizationIdAndStatus(organizationId, PaymentStatus.PENDING);
    }

    public Payment getPaymentByBooking(UUID bookingId) {
        return paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new RuntimeException("Payment not found for booking: " + bookingId));
    }

    public BigDecimal calculateBalance(UUID organizationId) {
        return paymentRepository.findByOrganizationId(organizationId).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public EventTermPaymentOverviewResponse getEventTermPaymentOverview(UUID eventTermId) {
        EventTermClientResponse eventTerm = eventServiceClient.getEventTerm(eventTermId);
        if (eventTerm == null) {
            throw new RuntimeException("Event term not found: " + eventTermId);
        }

        List<BookingClientResponse> bookings = bookingServiceClient.getBookingsForEventTerm(eventTermId);
        List<UUID> bookingIds = bookings.stream()
                .map(BookingClientResponse::getId)
                .filter(Objects::nonNull)
                .toList();

        List<Payment> payments = bookingIds.isEmpty()
                ? Collections.emptyList()
                : paymentRepository.findByBookingIdIn(bookingIds);

        Map<UUID, Payment> paymentsByBookingId = payments.stream()
                .collect(Collectors.toMap(Payment::getBookingId, Function.identity(), (first, second) -> first));

        List<EventTermPaymentParticipantResponse> participants = bookings.stream()
                .map(booking -> toParticipantResponse(booking, paymentsByBookingId.get(booking.getId()), eventTerm.getPrice()))
                .toList();

        long billableBookingCount = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .count();

        BigDecimal totalExpectedAmount = amountOrZero(eventTerm.getPrice())
                .multiply(BigDecimal.valueOf(billableBookingCount));
        BigDecimal totalPaidAmount = sumByStatus(payments, PaymentStatus.PAID);
        BigDecimal totalPendingAmount = sumByStatus(payments, PaymentStatus.PENDING);
        BigDecimal totalRefundedAmount = sumByStatus(payments, PaymentStatus.REFUNDED);

        long missingBillablePaymentCount = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .filter(booking -> !paymentsByBookingId.containsKey(booking.getId()))
                .count();
        BigDecimal missingPaymentAmount = amountOrZero(eventTerm.getPrice())
                .multiply(BigDecimal.valueOf(missingBillablePaymentCount));

        EventTermPaymentOverviewResponse response = new EventTermPaymentOverviewResponse();
        response.setEventTermId(eventTerm.getId());
        response.setEventId(eventTerm.getEventId());
        response.setEventName(eventTerm.getEventName());
        response.setEventLocation(eventTerm.getEventLocation());
        response.setPrice(amountOrZero(eventTerm.getPrice()));
        response.setStartDateTime(eventTerm.getStartDateTime());
        response.setEndDateTime(eventTerm.getEndDateTime());
        response.setMinParticipants(eventTerm.getMinParticipants());
        response.setMaxParticipants(eventTerm.getMaxParticipants());
        response.setEventTermStatus(eventTerm.getStatus());

        response.setBookingCount(bookings.size());
        response.setBillableBookingCount(billableBookingCount);
        response.setPaidCount(countByStatus(payments, PaymentStatus.PAID));
        response.setPendingCount(countByStatus(payments, PaymentStatus.PENDING));
        response.setRefundedCount(countByStatus(payments, PaymentStatus.REFUNDED));
        response.setMissingPaymentCount(missingBillablePaymentCount);

        response.setTotalExpectedAmount(totalExpectedAmount);
        response.setTotalPaidAmount(totalPaidAmount);
        response.setTotalPendingAmount(totalPendingAmount);
        response.setTotalRefundedAmount(totalRefundedAmount);
        response.setTotalOpenAmount(totalPendingAmount.add(missingPaymentAmount));
        response.setParticipants(participants);
        return response;
    }

    private EventTermPaymentParticipantResponse toParticipantResponse(
            BookingClientResponse booking,
            Payment payment,
            BigDecimal eventPrice) {
        EventTermPaymentParticipantResponse response = new EventTermPaymentParticipantResponse();
        response.setBookingId(booking.getId());
        response.setFamilyMemberId(booking.getFamilyMemberId());
        response.setBookingStatus(booking.getStatus());
        response.setBookedAt(booking.getBookedAt());

        if (payment == null) {
            response.setAmount(amountOrZero(eventPrice));
            response.setPaymentAvailable(false);
            return response;
        }

        response.setPaymentId(payment.getId());
        response.setAmount(amountOrZero(payment.getAmount()));
        response.setPaymentStatus(payment.getStatus());
        response.setPaidAt(payment.getPaidAt());
        response.setRefundedAt(payment.getRefundedAt());
        response.setNote(payment.getNote());
        response.setPaymentAvailable(true);
        return response;
    }

    private BigDecimal sumByStatus(List<Payment> payments, PaymentStatus status) {
        return payments.stream()
                .filter(payment -> payment.getStatus() == status)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long countByStatus(List<Payment> payments, PaymentStatus status) {
        return payments.stream()
                .filter(payment -> payment.getStatus() == status)
                .count();
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
