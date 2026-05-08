package com.holidayplanner.identityservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityEventListener {

    @KafkaListener(topics = "holiday-planner.booking.cancelled", groupId = "identity-service")
    public void handleBookingCancelled(String message) {
        try {
            log.info("Received BookingCancelled event");
        } catch (Exception e) {
            log.error("Error handling BookingCancelled event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "holiday-planner.payment.refunded", groupId = "identity-service")
    public void handlePaymentRefunded(String message) {
        try {
            log.info("Received PaymentRefunded event");
        } catch (Exception e) {
            log.error("Error handling PaymentRefunded event: {}", e.getMessage());
        }
    }
}
