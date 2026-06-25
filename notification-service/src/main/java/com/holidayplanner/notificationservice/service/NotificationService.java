package com.holidayplanner.notificationservice.service;

import com.holidayplanner.notificationservice.client.BookingServiceClient;
import com.holidayplanner.notificationservice.client.BookletServiceClient;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final JavaMailSender mailSender;
  private final BookletServiceClient bookletServiceClient;
  private final BookingServiceClient bookingServiceClient;

  public void sendEmail(List<String> to, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(to.toArray(String[]::new));
    message.setSubject(subject);
    message.setText(body);
    mailSender.send(message);
    log.info("Email sent to {} recipients with subject '{}'", to.size(), subject);
  }

  public void notifyBookingCreated(
      String parentEmail, String eventName, String termDate, String status) {
    String subject;
    String body;

    switch (normalizeStatus(status)) {
      case "CONFIRMED" -> {
        subject = "Booking Confirmed – " + eventName;
        body =
            String.format(
                "Your booking for \"%s\" on %s has been confirmed!\n\nHoliday Planner Team",
                eventName, termDate);
      }
      case "WAITLISTED" -> {
        subject = "Booking Waitlisted – " + eventName;
        body =
            String.format(
                "Your booking for \"%s\" on %s has been created and you are on the waiting"
                    + " list.\n\n"
                    + "Holiday Planner Team",
                eventName, termDate);
      }
      default -> {
        log.warn("Ignoring BookingCreated event with unsupported status: {}", status);
        return;
      }
    }

    sendEmail(List.of(parentEmail), subject, body);
  }

  public void notifyWaitlistPromoted(String parentEmail, String eventName, String termDate) {
    String subject = "Booking Confirmed – " + eventName;
    String body =
        String.format(
            "Your booking for \"%s\" on %s has been confirmed!\n\nHoliday Planner Team",
            eventName, termDate);
    sendEmail(List.of(parentEmail), subject, body);
  }

  private String normalizeStatus(String status) {
    return status == null ? "" : status.trim().toUpperCase();
  }

  public void notifyBookingCancelled(
      String parentEmail, String eventName, String termDate, String cancelledBy) {
    String subject;
    String body;

    switch (normalizeCancelledBy(cancelledBy)) {
      case "EVENT_OWNER" -> {
        subject = "Your Booking Was Cancelled – " + eventName;
        body =
            String.format(
                "Your booking for \"%s\" on %s has been cancelled by the event owner.\n\n"
                    + "Holiday Planner Team",
                eventName, termDate);
      }
      case "USER", "PARENT" -> {
        subject = "Booking Cancelled – " + eventName;
        body =
            String.format(
                "Your cancellation for \"%s\" on %s has been recorded.\n\nHoliday Planner Team",
                eventName, termDate);
      }
      default -> {
        log.warn("Ignoring BookingCancelled event with unsupported cancelledBy: {}", cancelledBy);
        return;
      }
    }

    sendEmail(List.of(parentEmail), subject, body);
  }

  private String normalizeCancelledBy(String cancelledBy) {
    return cancelledBy == null ? "" : cancelledBy.trim().replace('-', '_').toUpperCase();
  }

  public void notifyCaregiverWithParticipantListPdf(
      UUID eventTermId, String caregiverEmail, String eventName, String termDate)
      throws MessagingException {
    byte[] pdf = bookletServiceClient.getParticipantListPdf(eventTermId);
    String subject = "Participant List – " + eventName + " – " + termDate;
    String body =
        String.format(
            "Hello,\n\nAttached is the participant list for \"%s\" on %s.\n\nHoliday Planner Team",
            eventName, termDate);
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true);
    helper.setTo(caregiverEmail);
    helper.setSubject(subject);
    helper.setText(body);
    helper.addAttachment("participants-" + eventName + ".pdf", new ByteArrayResource(pdf));
    mailSender.send(message);
    log.info("Participant list email sent to {}", caregiverEmail);
  }

  public void notifyRefund(String parentEmail, String eventName, java.math.BigDecimal amount) {
    String subject = "Refund Processed – " + eventName;
    String body =
        String.format(
            "Your refund of %.2f for \"%s\" has been processed.\n\nHoliday Planner Team",
            amount, eventName);
    sendEmail(List.of(parentEmail), subject, body);
  }

  public void notifyEventTermCancelled(
      UUID eventTermId, List<String> caregiverEmails, String eventName, String termDate) {
    LinkedHashSet<String> recipients = new LinkedHashSet<>();
    if (caregiverEmails != null) {
      recipients.addAll(caregiverEmails);
    }
    recipients.addAll(bookingServiceClient.getParticipantParentEmails(eventTermId));
    if (recipients.isEmpty()) {
      log.warn("No recipients found for cancelled event term {}", eventTermId);
      return;
    }

    String subject = "Event Cancelled – " + eventName;
    String body =
        String.format(
            "The event \"%s\" on %s has been cancelled.\n\nHoliday Planner Team",
            eventName, termDate);
    sendEmail(List.copyOf(recipients), subject, body);
  }
}
