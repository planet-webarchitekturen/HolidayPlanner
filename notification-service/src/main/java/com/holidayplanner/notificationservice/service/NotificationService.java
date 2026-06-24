package com.holidayplanner.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;

    public void sendEmail(List<String> to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to.toArray(String[]::new));
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Email sent to {} recipients with subject '{}'", to.size(), subject);
    }

    public void notifyBookingConfirmed(String parentEmail, String eventName, String termDate) {
        String subject = "Booking Confirmed – " + eventName;
        String body = String.format(
                "Your booking for \"%s\" on %s has been confirmed!\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(List.of(parentEmail), subject, body);
    }

    public void notifyBookingWaitlisted(String parentEmail, String eventName, String termDate) {
        String subject = "Booking Waitlisted – " + eventName;
        String body = String.format(
                "Your booking for \"%s\" on %s is on the waiting list.\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(List.of(parentEmail), subject, body);
    }

    public void notifyTermCancelled(String parentEmail, String eventName, String termDate) {
        String subject = "Event Cancelled – " + eventName;
        String body = String.format(
                "Unfortunately, \"%s\" on %s has been cancelled.\n\nWe apologise for the inconvenience.\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(List.of(parentEmail), subject, body);
    }

    public void notifyBookingCancelledByOwner(String parentEmail, String eventName, String termDate) {
        String subject = "Your Booking Was Cancelled – " + eventName;
        String body = String.format(
                "Your booking for \"%s\" on %s has been cancelled by the event owner.\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(List.of(parentEmail), subject, body);
    }

    public void notifyBookingCancelledByUser(String parentEmail, String eventName, String termDate) {
        String subject = "Booking Cancelled – " + eventName;
        String body = String.format(
                "Your cancellation for \"%s\" on %s has been recorded.\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(List.of(parentEmail), subject, body);
    }

    public void notifyCaregiverWithParticipants(String caregiverEmail, String eventName,
                                                 String termDate, List<String> participantNames) {
        String subject = "Participant List – " + eventName + " – " + termDate;
        String participantList = String.join("\n- ", participantNames);
        String body = String.format(
                "Hello,\n\nHere is the participant list for \"%s\" on %s:\n\n- %s\n\nHoliday Planner Team",
                eventName, termDate, participantList);
        sendEmail(List.of(caregiverEmail), subject, body);
    }

    public void notifyRefund(String parentEmail, String eventName, java.math.BigDecimal amount) {
        String subject = "Refund Processed – " + eventName;
        String body = String.format(
                "Your refund of %.2f for \"%s\" has been processed.\n\nHoliday Planner Team",
                amount, eventName);
        sendEmail(List.of(parentEmail), subject, body);
    }

    public void notifyCaregiversOfAutoCancellation(List<String> caregiverEmails,
                                                    String eventName, String termDate) {
        String subject = "Event Auto-Cancelled – " + eventName;
        String body = String.format(
                "The event \"%s\" on %s has been automatically cancelled as the minimum number of participants was not reached.\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(caregiverEmails, subject, body);
    }
}
