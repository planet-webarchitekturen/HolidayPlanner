package com.holidayplanner.identityservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka Event Consumer for Identity Service.
 * 
 * Listens to events from other services:
 * - BookingCancelled (from booking-service): For cascade cleanup scenarios
 * - PaymentRefunded (from payment-service): For future refund notifications
 * 
 * Currently implements graceful handlers that log events.
 * As system evolves, these can trigger domain actions (e.g., family member cleanup).
 * 
 * Handler pattern:
 * - Validate event structure
 * - Log with context
 * - Execute business logic (currently no-op)
 * - On error: log warning (consumer continues, manual intervention available via dead letter queue)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityEventListener {

    /**
    * Listen to holiday-planner.booking.cancelled events.
     * 
     * Context: When an event term is cancelled or a booking is cancelled,
     * the identity service may need to react (e.g., notify user, cleanup).
     * 
     * @param event DomainEvent wrapping BookingCancelledEvent
     */
    @KafkaListener(topics = "holiday-planner.booking.cancelled", groupId = "identity-service")
    public void handleBookingCancelled(DomainEvent event) {
        try {
            if (!"BookingCancelled".equals(event.getEventType())) {
                log.warn("Unexpected event type in holiday-planner.booking.cancelled: {}", event.getEventType());
                return;
            }
            
                log.info("Received BookingCancelled event: timestamp={}", event.getTimestamp());
            
            // Future: Extract payload and check if all member's bookings are cancelled
            // if (allBookingsCancelled(memberId)) { cleanup or notify }
            
        } catch (Exception e) {
            log.error("Error handling BookingCancelled event: {}", e.getMessage(), e);
            // Note: Exception does not re-throw; consumer continues
            // In production, consider sending to DLQ for manual inspection
        }
    }

    /**
    * Listen to holiday-planner.payment.refunded events.
     * 
     * Context: When a payment is refunded (e.g., event cancelled, booking refunded),
     * identity service may notify the user.
     * 
     * @param event DomainEvent wrapping PaymentRefundedEvent
     */
    @KafkaListener(topics = "holiday-planner.payment.refunded", groupId = "identity-service")
    public void handlePaymentRefunded(DomainEvent event) {
        try {
            if (!"PaymentRefunded".equals(event.getEventType())) {
                log.warn("Unexpected event type in holiday-planner.payment.refunded: {}", event.getEventType());
                return;
            }
            
                log.info("Received PaymentRefunded event: timestamp={}", event.getTimestamp());
            
            // Future: Notify user about refund status
            // sendNotificationService.notifyRefundProcessed(userId, amount)
            
        } catch (Exception e) {
            log.error("Error handling PaymentRefunded event: {}", e.getMessage(), e);
        }
    }
}
