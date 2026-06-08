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

    // Send a single email
    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        log.info("Email delivery disabled; would send email to {} with subject '{}'", to, subject);
    }

    // Send bulk emails to multiple recipients
    public void sendBulkEmail(List<String> recipients, String subject, String body) {
        recipients.forEach(recipient -> sendEmail(recipient, subject, body));
        log.info("Bulk email sent to {} recipients", recipients.size());
    }

    // Notify parent that booking is confirmed
    public void notifyBookingConfirmed(String parentEmail, String eventName, String termDate) {
        String subject = "Booking Confirmed – " + eventName;
        String body = String.format(
                "Your booking for \"%s\" on %s has been confirmed!\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(parentEmail, subject, body);
    }

    // Notify parent that booking is on the waiting list
    public void notifyBookingWaitlisted(String parentEmail, String eventName, String termDate) {
        String subject = "Booking Waitlisted – " + eventName;
        String body = String.format(
                "Your booking for \"%s\" on %s is on the waiting list.\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(parentEmail, subject, body);
    }

    // Notify parent that an event term was cancelled
    public void notifyTermCancelled(String parentEmail, String eventName, String termDate) {
        String subject = "Event Cancelled – " + eventName;
        String body = String.format(
                "Unfortunately, \"%s\" on %s has been cancelled.\n\nWe apologise for the inconvenience.\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(parentEmail, subject, body);
    }

    // Notify parent that their booking was cancelled by the event owner
    public void notifyBookingCancelledByOwner(String parentEmail, String eventName, String termDate) {
        String subject = "Your Booking Was Cancelled – " + eventName;
        String body = String.format(
                "Your booking for \"%s\" on %s has been cancelled by the event owner.\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(parentEmail, subject, body);
    }

    // Notify parent that their own cancellation was recorded
    public void notifyBookingCancelledByUser(String parentEmail, String eventName, String termDate) {
        String subject = "Booking Cancelled – " + eventName;
        String body = String.format(
                "Your cancellation for \"%s\" on %s has been recorded.\n\nHoliday Planner Team",
                eventName, termDate);
        sendEmail(parentEmail, subject, body);
    }

    // Notify caregiver with participant list one day before event
    public void notifyCaregiverWithParticipants(String caregiverEmail, String eventName,
                                                 String termDate, List<String> participantNames) {
        String subject = "Participant List – " + eventName + " – " + termDate;
        String participantList = String.join("\n- ", participantNames);
        String body = String.format(
                "Hello,\n\nHere is the participant list for \"%s\" on %s:\n\n- %s\n\nHoliday Planner Team",
                eventName, termDate, participantList);
        sendEmail(caregiverEmail, subject, body);
    }

    // Notify parent about refund
    public void notifyRefund(String parentEmail, String eventName, java.math.BigDecimal amount) {
        String subject = "Refund Processed – " + eventName;
        String body = String.format(
                "Your refund of %.2f for \"%s\" has been processed.\n\nHoliday Planner Team",
                amount, eventName);
        sendEmail(parentEmail, subject, body);
    }

    // Notify all caregivers about automatic cancellation
    public void notifyCaregiversOfAutoCancellation(List<String> caregiverEmails,
                                                    String eventName, String termDate) {
        String subject = "Event Auto-Cancelled – " + eventName;
        String body = String.format(
                "The event \"%s\" on %s has been automatically cancelled as the minimum number of participants was not reached.\n\nHoliday Planner Team",
                eventName, termDate);
        sendBulkEmail(caregiverEmails, subject, body);
    }
}
