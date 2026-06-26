package com.holidayplanner.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.holidayplanner.notificationservice.client.BookingServiceClient;
import com.holidayplanner.notificationservice.client.BookletServiceClient;
import com.holidayplanner.shared.model.BookingStatus;
import com.holidayplanner.shared.model.CancelledBy;
import com.holidayplanner.shared.model.PaymentMethod;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.Arrays;
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
  void sendEmailSkipsEmptyRecipients() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.sendEmail(Arrays.asList(null, "", " "), "Subject", "Body");

    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void notifyBookingCreatedBuildsConfirmedEmail() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCreated(
        "parent@example.test",
        "Bike Adventure",
        "2026-06-15T09:00",
        BookingStatus.CONFIRMED,
        "Main gate",
        PaymentMethod.BANK_TRANSFER,
        new BigDecimal("12.50"));

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getTo()).containsExactly("parent@example.test");
    assertThat(message.getSubject()).isEqualTo("Booking Confirmed – Bike Adventure");
    assertThat(message.getText())
        .isEqualTo(
            "Your booking for \"Bike Adventure\" on 2026-06-15T09:00 has been confirmed!\n"
                + "Meeting point: Main gate\n"
                + "Payment method: BANK_TRANSFER\n"
                + "Amount: 12.50\n\n"
                + "Holiday Planner Team");
  }

  @Test
  void notifyBookingCreatedBuildsWaitlistedEmail() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCreated(
        "parent@example.test",
        "Bike Adventure",
        "2026-06-15T09:00",
        BookingStatus.WAITLISTED,
        null,
        null,
        null);

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
        eventTermId, List.of("caregiver@example.test"), "Bike Adventure", "2026-06-15T09:00");

    verify(bookletServiceClient).getParticipantListPdf(eventTermId);
    verify(mailSender).send(any(MimeMessage.class));
  }

  @Test
  void notifyCaregiverWithParticipantListPdfSkipsBlankCaregivers() throws Exception {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    BookletServiceClient bookletServiceClient = mock(BookletServiceClient.class);
    UUID eventTermId = UUID.randomUUID();
    NotificationService notificationService =
        new NotificationService(mailSender, bookletServiceClient, mock(BookingServiceClient.class));

    notificationService.notifyCaregiverWithParticipantListPdf(
        eventTermId, List.of(" "), "Bike Adventure", "2026-06-15T09:00");

    verify(bookletServiceClient, never()).getParticipantListPdf(eventTermId);
    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void notifyBookingCreatedUsesGenericEmailForMissingStatus() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCreated(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", null, null, null, null);

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getSubject()).isEqualTo("Booking Created – Bike Adventure");
    assertThat(message.getText())
        .isEqualTo(
            "Your booking for \"Bike Adventure\" on 2026-06-15T09:00 has been created.\n\n"
                + "Holiday Planner Team");
  }

  @Test
  void notifyBookingCreatedUsesGenericEmailForUnhandledStatus() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCreated(
        "parent@example.test",
        "Bike Adventure",
        "2026-06-15T09:00",
        BookingStatus.CANCELLED,
        null,
        null,
        null);

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getSubject()).isEqualTo("Booking Created – Bike Adventure");
    assertThat(message.getText())
        .isEqualTo(
            "Your booking for \"Bike Adventure\" on 2026-06-15T09:00 has been created.\n\n"
                + "Holiday Planner Team");
  }

  @Test
  void notifyBookingCancelledBuildsParentCancellationEmail() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCancelled(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", CancelledBy.PARENT);

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
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", CancelledBy.EVENT_OWNER);

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
  void notifyBookingCancelledSkipsTermCancelled() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCancelled(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", CancelledBy.TERM_CANCELLED);

    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void notifyBookingCancelledUsesGenericEmailForMissingCancelledBy() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    NotificationService notificationService = notificationService(mailSender);

    notificationService.notifyBookingCancelled(
        "parent@example.test", "Bike Adventure", "2026-06-15T09:00", null);

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getSubject()).isEqualTo("Booking Cancelled – Bike Adventure");
    assertThat(message.getText())
        .isEqualTo(
            "Your booking for \"Bike Adventure\" on 2026-06-15T09:00 has been cancelled.\n\n"
                + "Holiday Planner Team");
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
    assertThat(message.getSubject()).isEqualTo("Event Term Cancelled – Bike Adventure");
    assertThat(message.getText())
        .isEqualTo(
            "The event term \"Bike Adventure\" on 2026-06-15T09:00 has been cancelled.\n\n"
                + "Holiday Planner Team");
  }

  @Test
  void notifyParticipantsFetchesParentEmailsWhenSending() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    BookingServiceClient bookingServiceClient = mock(BookingServiceClient.class);
    UUID eventTermId = UUID.randomUUID();
    when(bookingServiceClient.getParticipantParentEmails(eventTermId))
        .thenReturn(List.of("parent@example.test"));
    NotificationService notificationService =
        new NotificationService(mailSender, mock(BookletServiceClient.class), bookingServiceClient);

    notificationService.notifyParticipants(eventTermId, "Subject", "Body");

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getTo()).containsExactly("parent@example.test");
    assertThat(message.getSubject()).isEqualTo("Subject");
    assertThat(message.getText()).isEqualTo("Body");
  }

  private NotificationService notificationService(JavaMailSender mailSender) {
    return new NotificationService(
        mailSender, mock(BookletServiceClient.class), mock(BookingServiceClient.class));
  }
}
