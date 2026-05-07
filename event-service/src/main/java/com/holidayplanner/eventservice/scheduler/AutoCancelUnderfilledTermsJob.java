package com.holidayplanner.eventservice.scheduler;

import com.holidayplanner.eventservice.command.CancellationActor;
import com.holidayplanner.eventservice.command.EventTermCommandService;
import com.holidayplanner.eventservice.port.BookingServicePort;
import com.holidayplanner.eventservice.query.EventTermQueryService;
import com.holidayplanner.shared.model.EventTerm;
import com.holidayplanner.shared.model.EventTermStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCancelUnderfilledTermsJob {

    private final EventTermQueryService eventTermQueryService;
    private final EventTermCommandService eventTermCommandService;
    private final BookingServicePort bookingServicePort;

    @Scheduled(cron = "${event-service.scheduler.auto-cancel-cron:0 0 3 * * *}")
    public void autoCancelUnderfilledTerms() {
        LocalDateTime now = LocalDateTime.now();
        for (EventTerm term : eventTermQueryService.findActiveTermsStartingWithin24Hours(now)) {
            try {
                long confirmed = bookingServicePort.getConfirmedBookingCount(term.getId());
                if (confirmed < term.getMinParticipants()) {
                    log.info("Auto-cancelling event term {} — confirmed {} < min {}", term.getId(), confirmed,
                            term.getMinParticipants());
                    eventTermCommandService.changeEventTermStatus(term.getId(), EventTermStatus.CANCELLED,
                            CancellationActor.SYSTEM);
                }
            } catch (Exception e) {
                log.error("Failed auto-cancel check for event term {}", term.getId(), e);
            }
        }
    }
}
