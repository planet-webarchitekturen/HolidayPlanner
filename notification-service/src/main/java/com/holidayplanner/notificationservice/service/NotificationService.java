package com.holidayplanner.notificationservice.service;

import com.holidayplanner.notificationservice.client.BookingServiceClient;
import com.holidayplanner.notificationservice.client.BookletServiceClient;
import com.holidayplanner.shared.model.BookingStatus;
import com.holidayplanner.shared.model.CancelledBy;
import com.holidayplanner.shared.model.PaymentMethod;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
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
    List<String> recipients = cleanRecipients(to);
    if (recipients.isEmpty()) {
      log.warn("No email recipients for subject '{}' — email skipped", subject);
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(recipients.toArray(String[]::new));
    message.setSubject(subject);
    message.setText(body);
    mailSender.send(message);
    log.info("Email sent to {} recipients with subject '{}'", recipients.size(), subject);
  }

  private List<String> cleanRecipients(List<String> recipients) {
    if (recipients == null) {
      return List.of();
    }
    return recipients.stream().filter(email -> email != null && !email.isBlank()).toList();
  }

  public void notifyBookingCreated(
      String parentEmail,
      String eventName,
      String termDate,
      BookingStatus status,
      String meetingPoint,
      PaymentMethod paymentMethod,
      BigDecimal amount) {
    String subject;
    String body;
    String details = bookingDetails(meetingPoint, paymentMethod, amount);

    if (status == BookingStatus.CONFIRMED) {
      subject = "Booking Confirmed – " + eventName;
      body =
          String.format(
              "Your booking for \"%s\" on %s has been confirmed!%s\n\nHoliday Planner Team",
              eventName, termDate, details);
    } else if (status == BookingStatus.WAITLISTED) {
      subject = "Booking Waitlisted – " + eventName;
      body =
          String.format(
              "Your booking for \"%s\" on %s has been created and you are on the waiting"
                  + " list.%s\n\n"
                  + "Holiday Planner Team",
              eventName, termDate, details);
    } else {
      subject = "Booking Created – " + eventName;
      body =
          String.format(
              "Your booking for \"%s\" on %s has been created.%s\n\nHoliday Planner Team",
              eventName, termDate, details);
    }

    sendEmail(Collections.singletonList(parentEmail), subject, body);
  }

  private String bookingDetails(
      String meetingPoint, PaymentMethod paymentMethod, BigDecimal amount) {
    StringBuilder details = new StringBuilder();
    if (meetingPoint != null && !meetingPoint.isBlank()) {
      details.append("\nMeeting point: ").append(meetingPoint);
    }
    if (paymentMethod != null) {
      details.append("\nPayment method: ").append(paymentMethod);
    }
    if (amount != null) {
      details.append("\nAmount: ").append(amount);
    }
    return details.toString();
  }

  public void notifyWaitlistPromoted(String parentEmail, String eventName, String termDate) {
    String subject = "Booking Confirmed – " + eventName;
    String body =
        String.format(
            "Your booking for \"%s\" on %s has been confirmed!\n\nHoliday Planner Team",
            eventName, termDate);
    sendEmail(Collections.singletonList(parentEmail), subject, body);
  }

  public void notifyBookingCancelled(
      String parentEmail, String eventName, String termDate, CancelledBy cancelledBy) {
    if (cancelledBy == CancelledBy.SYSTEM) {
      log.info("Skipping booking cancellation email because event term cancellation handles it");
      return;
    }

    String subject;
    String body;

    if (cancelledBy == CancelledBy.EVENT_OWNER) {
      subject = "Your Booking Was Cancelled – " + eventName;
      body =
          String.format(
              "Your booking for \"%s\" on %s has been cancelled by the event owner.\n\n"
                  + "Holiday Planner Team",
              eventName, termDate);
    } else if (cancelledBy == CancelledBy.USER) {
      subject = "Booking Cancelled – " + eventName;
      body =
          String.format(
              "Your cancellation for \"%s\" on %s has been recorded.\n\nHoliday Planner Team",
              eventName, termDate);
    } else {
      subject = "Booking Cancelled – " + eventName;
      body =
          String.format(
              "Your booking for \"%s\" on %s has been cancelled.\n\nHoliday Planner Team",
              eventName, termDate);
    }

    sendEmail(Collections.singletonList(parentEmail), subject, body);
  }

  public void notifyCaregiverWithParticipantListPdf(
      UUID eventTermId, List<String> caregiverEmails, String eventName, String termDate)
      throws MessagingException {
    List<String> recipients = cleanRecipients(caregiverEmails);
    if (recipients.isEmpty()) {
      log.warn("No caregivers found for participant list email for event term {}", eventTermId);
      return;
    }
    byte[] pdf = bookletServiceClient.getParticipantListPdf(eventTermId);
    String subject = "Participant List – " + eventName + " – " + termDate;
    String body =
        String.format(
            "Hello,\n\nAttached is the participant list for \"%s\" on %s.\n\nHoliday Planner Team",
            eventName, termDate);
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true);
    helper.setTo(recipients.toArray(String[]::new));
    helper.setSubject(subject);
    helper.setText(body);
    helper.addAttachment("participants-" + eventName + ".pdf", new ByteArrayResource(pdf));
    mailSender.send(message);
    log.info("Participant list email sent to {} caregivers", recipients.size());
  }

  public void notifyRefund(String parentEmail, String eventName, java.math.BigDecimal amount) {
    String subject = "Refund Processed – " + eventName;
    String body =
        String.format(
            "Your refund of %.2f for \"%s\" has been processed.\n\nHoliday Planner Team",
            amount, eventName);
    sendEmail(Collections.singletonList(parentEmail), subject, body);
  }

  public void notifyParticipants(UUID eventTermId, String subject, String body) {
    List<String> recipients = bookingServiceClient.getParticipantParentEmails(eventTermId);
    sendEmail(recipients, subject, body);
  }

  public void notifyEventTermCancelled(
      UUID eventTermId, List<String> caregiverEmails, String eventName, String termDate) {
    LinkedHashSet<String> recipients = new LinkedHashSet<>();
    if (caregiverEmails != null) {
      recipients.addAll(caregiverEmails);
    }
    recipients.addAll(bookingServiceClient.getParticipantParentEmails(eventTermId));

    String subject = "Event Term Cancelled – " + eventName;
    String body =
        String.format(
            "The event term \"%s\" on %s has been cancelled.\n\nHoliday Planner Team",
            eventName, termDate);
    sendEmail(new ArrayList<>(recipients), subject, body);
  }
}
