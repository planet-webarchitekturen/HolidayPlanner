package com.holidayplanner.bookletservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.holidayplanner.bookletservice.client.BookingServiceClient;
import com.holidayplanner.bookletservice.client.EventDto;
import com.holidayplanner.bookletservice.client.EventServiceClient;
import com.holidayplanner.bookletservice.client.EventTermDto;
import com.holidayplanner.bookletservice.client.OrganizationDto;
import com.holidayplanner.bookletservice.client.OrganizationServiceClient;
import com.holidayplanner.bookletservice.client.SponsorDto;
import com.holidayplanner.bookletservice.client.TeamMemberDto;
import com.holidayplanner.bookletservice.kafka.BookletEventProducer;
import com.holidayplanner.shared.kafka.payload.ParticipantListPdfGeneratedPayload;
import com.holidayplanner.shared.kafka.payload.ParticipantListRequestedPayload;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class BookletServiceTest {

  @TempDir private Path tempDir;

  @Test
  void generatesOrganizationBookletFromUpstreamData() throws Exception {
    OrganizationServiceClient organizationServiceClient = mock(OrganizationServiceClient.class);
    EventServiceClient eventServiceClient = mock(EventServiceClient.class);
    UUID organizationId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    when(organizationServiceClient.getOrganization(organizationId))
        .thenReturn(
            new OrganizationDto(
                organizationId, "Summer Club", null, LocalDateTime.of(2026, 5, 1, 8, 0)));
    when(organizationServiceClient.getTeamMembers(organizationId))
        .thenReturn(
            List.of(
                new TeamMemberDto(
                    UUID.randomUUID(),
                    organizationId,
                    UUID.randomUUID(),
                    "Ada",
                    "Admin",
                    "ada@example.test",
                    "ORGANIZATION_OWNER")));
    when(organizationServiceClient.getSponsors(organizationId))
        .thenReturn(
            List.of(
                new SponsorDto(UUID.randomUUID(), organizationId, "Local Bank", BigDecimal.TEN)));
    when(eventServiceClient.getEventsByOrganization(organizationId))
        .thenReturn(
            List.of(
                new EventDto(
                    eventId,
                    organizationId,
                    UUID.randomUUID(),
                    "Bike Day",
                    "Ride together",
                    null,
                    "Dornbirn",
                    "Station",
                    BigDecimal.valueOf(12),
                    "BANK_TRANSFER",
                    8,
                    14,
                    null)));
    when(eventServiceClient.getTermsForEvent(eventId))
        .thenReturn(
            List.of(
                new EventTermDto(
                    UUID.randomUUID(),
                    LocalDateTime.of(2026, 7, 2, 9, 0),
                    LocalDateTime.of(2026, 7, 2, 12, 0),
                    5,
                    10,
                    "ACTIVE",
                    List.of()),
                new EventTermDto(
                    UUID.randomUUID(),
                    LocalDateTime.of(2026, 7, 1, 9, 0),
                    LocalDateTime.of(2026, 7, 1, 12, 0),
                    4,
                    8,
                    "ACTIVE",
                    List.of())));

    BookletService bookletService =
        new BookletService(
            organizationServiceClient,
            eventServiceClient,
            mock(BookingServiceClient.class),
            mock(BookletEventProducer.class),
            tempDir.toString());

    String text = pdfText(bookletService.generateOrganizationBooklet(organizationId));

    assertThat(text).contains("Holiday Planner Booklet");
    assertThat(text).contains("Holiday Planner web site");
    assertThat(text).contains("Ada Admin | ORGANIZATION_OWNER | ada@example.test");
    assertThat(text).contains("--- Event Term Index ---");
    assertThat(text).contains("Bike Day");
    assertThat(text).contains("4-8 participants");
    assertThat(text).contains("Local Bank | 10 EUR");
    assertThat(text.indexOf("01.07.2026 09:00")).isLessThan(text.indexOf("02.07.2026 09:00"));
  }

  @Test
  void createsStoresAndPublishesParticipantListPdf() throws Exception {
    BookingServiceClient bookingServiceClient = mock(BookingServiceClient.class);
    BookletEventProducer bookletEventProducer = mock(BookletEventProducer.class);
    BookletService bookletService =
        new BookletService(
            mock(OrganizationServiceClient.class),
            mock(EventServiceClient.class),
            bookingServiceClient,
            bookletEventProducer,
            tempDir.toString());
    UUID eventTermId = UUID.randomUUID();
    when(bookingServiceClient.getParticipantDisplayNames(eventTermId))
        .thenReturn(List.of("Anna", "Ben"));

    bookletService.createParticipantListPdf(
        new ParticipantListRequestedPayload(
            eventTermId, "caregiver@example.test", "Bike Adventure", "2026-06-15T09:00"));

    byte[] pdf = bookletService.readParticipantListPdf(eventTermId);
    assertThat(pdf).startsWith("%PDF".getBytes());
    ArgumentCaptor<ParticipantListPdfGeneratedPayload> captor =
        ArgumentCaptor.forClass(ParticipantListPdfGeneratedPayload.class);
    verify(bookletEventProducer).publishParticipantListPdfGenerated(captor.capture());
    assertThat(captor.getValue().getEventTermId()).isEqualTo(eventTermId);
    assertThat(captor.getValue().getCaregiverEmail()).isEqualTo("caregiver@example.test");
  }

  private String pdfText(byte[] pdf) throws Exception {
    try (var document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document);
    }
  }
}
