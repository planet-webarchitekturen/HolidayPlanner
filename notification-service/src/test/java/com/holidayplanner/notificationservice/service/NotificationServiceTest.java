package com.holidayplanner.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.holidayplanner.notificationservice.client.BookingServiceClient;
import com.holidayplanner.notificationservice.client.BookletServiceClient;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class NotificationServiceTest {

  @Test
  void sendEmailDoesNotSendRealEmailYet() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.sendEmail(
        List.of("parent@example.test", "caregiver@example.test"), "Subject", "Body");

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getTo()).containsExactly("parent@example.test", "caregiver@example.test");
    assertThat(message.getSubject()).isEqualTo("Subject");
    assertThat(message.getText()).isEqualTo("Body");
  }

  @Test
  void notifyBookingCreatedBuildsConfirmedEmail() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCreated(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", "confirmed");

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getTo()).containsExactly("parent@example.test");
    assertThat(message.getSubject()).isEqualTo("Booking Confirmed – Bike Adventure");
    assertThat(message.getText())
        .isEqualTo(
            "Your booking for \"Bike Adventure\" on 2026-06-15T09:00 has been confirmed!\n\n"
                + "Holiday Planner Team");
  }

  @Test
  void notifyBookingCreatedBuildsWaitlistedEmail() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCreated(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", "WAITLISTED");

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getSubject()).isEqualTo("Booking Waitlisted – Bike Adventure");
    assertThat(message.getText())
        .isEqualTo(
            "Your booking for \"Bike Adventure\" on 2026-06-15T09:00 has been created and you are"
                + " on the waiting list.\n\n"
                + "Holiday Planner Team");
  }

  @Test
  void notifyCaregiverWithParticipantListPdfSendsAttachmentEmail() throws Exception {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    BookletServiceClient bookletServiceClient = mock(BookletServiceClient.class);
    MimeMessage mimeMessage = new MimeMessage((Session) null);
    UUID eventTermId = UUID.randomUUID();
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    when(bookletServiceClient.getParticipantListPdf(eventTermId)).thenReturn(new byte[] {1, 2, 3});
    NotificationService notificationService =
        new NotificationService(mailSender, bookletServiceClient, mock(BookingServiceClient.class));

    notificationService.notifyCaregiverWithParticipantListPdf(
        eventTermId, "caregiver@example.test", "Bike Adventure", "2026-06-15T09:00");

    verify(bookletServiceClient).getParticipantListPdf(eventTermId);
    verify(mailSender).send(any(MimeMessage.class));
  }

  @Test
  void notifyBookingCreatedIgnoresUnsupportedStatus() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCreated(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", "unknown");

    verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
  }

  @Test
  void notifyBookingCancelledBuildsParentCancellationEmail() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCancelled(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", "parent");

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getTo()).containsExactly("parent@example.test");
    assertThat(message.getSubject()).isEqualTo("Booking Cancelled – Bike Adventure");
    assertThat(message.getText())
        .isEqualTo(
            "Your cancellation for \"Bike Adventure\" on 2026-06-15T09:00 has been recorded.\n\n"
                + "Holiday Planner Team");
  }

  @Test
  void notifyBookingCancelledBuildsOwnerCancellationEmail() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCancelled(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", "event-owner");

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getSubject()).isEqualTo("Your Booking Was Cancelled – Bike Adventure");
    assertThat(message.getText())
        .isEqualTo(
            "Your booking for \"Bike Adventure\" on 2026-06-15T09:00 has been cancelled by the"
                + " event owner.\n\n"
                + "Holiday Planner Team");
  }

  @Test
  void notifyBookingCancelledIgnoresTermCancellation() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCancelled(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", "term-cancelled");

    verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
  }

  @Test
  void notifyBookingCancelledIgnoresUnsupportedCancelledBy() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCancelled(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", "unknown");

    verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
  }

  @Test
  void notifyEventTermCancelledSendsToCaregiversAndParticipants() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    BookingServiceClient bookingServiceClient = mock(BookingServiceClient.class);
    UUID eventTermId = UUID.randomUUID();
    when(bookingServiceClient.getParticipantParentEmails(eventTermId))
        .thenReturn(List.of("parent@example.test", "caregiver@example.test"));
    NotificationService notificationService =
        new NotificationService(mailSender, mock(BookletServiceClient.class), bookingServiceClient);

    notificationService.notifyEventTermCancelled(
        eventTermId, List.of("caregiver@example.test"), "Bike Adventure", "2026-06-15T09:00");

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getTo()).containsExactly("caregiver@example.test", "parent@example.test");
    assertThat(message.getSubject()).isEqualTo("Event Cancelled – Bike Adventure");
    assertThat(message.getText())
        .isEqualTo(
            "The event \"Bike Adventure\" on 2026-06-15T09:00 has been cancelled.\n\n"
                + "Holiday Planner Team");
  }

  private NotificationService notificationService(JavaMailSender mailSender) {
    return new NotificationService(
        mailSender, mock(BookletServiceClient.class), mock(BookingServiceClient.class));
  }
}
