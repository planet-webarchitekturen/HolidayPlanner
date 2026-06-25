package com.holidayplanner.bookletservice.service;

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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BookletService {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  private final OrganizationServiceClient organizationServiceClient;
  private final EventServiceClient eventServiceClient;
  private final BookingServiceClient bookingServiceClient;
  private final BookletEventProducer bookletEventProducer;
  private final Path storageDir;

  public BookletService(
      OrganizationServiceClient organizationServiceClient,
      EventServiceClient eventServiceClient,
      BookingServiceClient bookingServiceClient,
      BookletEventProducer bookletEventProducer,
      @org.springframework.beans.factory.annotation.Value(
              "${booklet.storage-dir:${java.io.tmpdir}/holidayplanner-booklets}")
          String storageDir) {
    this.organizationServiceClient = organizationServiceClient;
    this.eventServiceClient = eventServiceClient;
    this.bookingServiceClient = bookingServiceClient;
    this.bookletEventProducer = bookletEventProducer;
    this.storageDir = Path.of(storageDir);
  }

  public byte[] generateOrganizationBooklet(UUID organizationId) throws IOException {
    OrganizationDto organization = organizationServiceClient.getOrganization(organizationId);
    List<TeamMemberDto> teamMembers = organizationServiceClient.getTeamMembers(organizationId);
    List<SponsorDto> sponsors = organizationServiceClient.getSponsors(organizationId);
    List<EventDto> events = eventServiceClient.getEventsByOrganization(organizationId);
    List<EventWithTerms> eventsWithTerms =
        events.stream()
            .map(
                event -> new EventWithTerms(event, eventServiceClient.getTermsForEvent(event.id())))
            .toList();

    List<String> lines = new ArrayList<>();
    lines.add("=== " + or(organization.name(), "Organization") + " - Holiday Planner Booklet ===");
    lines.add("");
    lines.add(
        "Welcome to the Holiday Planner booklet for "
            + or(organization.name(), "this organization")
            + ".");
    lines.add("Please visit the Holiday Planner web site for current booking details and updates.");
    lines.add("");
    if (organization.bookingStartTime() != null) {
      lines.add("Bookings open: " + organization.bookingStartTime().format(DATE_FMT));
    }
    lines.add("");

    lines.add("--- Organization Team Contact ---");
    if (teamMembers.isEmpty()) {
      lines.add("No team contact information available.");
    } else {
      for (TeamMemberDto m : teamMembers) {
        lines.add(
            or(m.firstName(), "")
                + " "
                + or(m.lastName(), "")
                + " | "
                + or(m.role(), "")
                + " | "
                + or(m.email(), ""));
      }
    }
    lines.add("");

    List<TermEntry> termsByDate =
        eventsWithTerms.stream()
            .flatMap(
                event -> event.terms().stream().map(term -> new TermEntry(event.event(), term)))
            .sorted(
                Comparator.comparing(
                    entry -> entry.term().startDateTime(),
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

    lines.add("--- Event Term Index ---");
    if (termsByDate.isEmpty()) {
      lines.add("No event terms.");
    } else {
      for (TermEntry entry : termsByDate) {
        lines.add(
            fmt(entry.term().startDateTime())
                + " - "
                + or(entry.event().shortTitle(), "Untitled")
                + " ("
                + entry.term().minParticipants()
                + "-"
                + entry.term().maxParticipants()
                + " participants)");
      }
    }
    lines.add("");

    lines.add("--- Events ---");
    if (eventsWithTerms.isEmpty()) {
      lines.add("No events.");
    }
    for (EventWithTerms eventWithTerms : eventsWithTerms) {
      EventDto event = eventWithTerms.event();
      lines.add("");
      lines.add(or(event.shortTitle(), "Untitled"));
      lines.add(
          "Location: "
              + or(event.location(), "-")
              + " | Meeting point: "
              + or(event.meetingPoint(), "-"));
      lines.add(
          "Price: " + formatMoney(event.price()) + " | Payment: " + or(event.paymentMethod(), "-"));
      lines.add("Age: " + event.minimalAge() + "-" + event.maximalAge());
      lines.add("Description: " + or(event.description(), "-"));
      if (event.pictureUrl() != null && !event.pictureUrl().isBlank()) {
        lines.add("Picture: " + event.pictureUrl());
      }

      List<EventTermDto> terms =
          eventWithTerms.terms().stream()
              .sorted(
                  Comparator.comparing(
                      EventTermDto::startDateTime, Comparator.nullsLast(Comparator.naturalOrder())))
              .toList();
      if (terms.isEmpty()) {
        lines.add("Terms: none");
      } else {
        for (EventTermDto t : terms) {
          lines.add(
              "  Term: "
                  + fmt(t.startDateTime())
                  + " - "
                  + fmt(t.endDateTime())
                  + " | "
                  + t.minParticipants()
                  + "-"
                  + t.maxParticipants()
                  + " participants"
                  + " | "
                  + or(t.status(), "-"));
        }
      }
    }
    lines.add("");

    lines.add("--- Sponsors ---");
    if (sponsors.isEmpty()) {
      lines.add("No sponsors.");
    } else {
      for (SponsorDto s : sponsors) {
        lines.add(or(s.name(), "-") + " | " + formatMoney(s.amount()));
      }
    }

    return renderPdf(lines);
  }

  public void createParticipantListPdf(ParticipantListRequestedPayload payload) throws IOException {
    List<String> participantNames =
        bookingServiceClient.getParticipantDisplayNames(payload.getEventTermId());
    List<String> lines = new ArrayList<>();
    lines.add("Participant List");
    lines.add("");
    lines.add("Event: " + or(payload.getEventName(), "-"));
    lines.add("Date:  " + or(payload.getTermDate(), "-"));
    lines.add("Total: " + participantNames.size());
    lines.add("");
    int i = 1;
    for (String name : participantNames) {
      lines.add(i++ + ". " + or(name, "-"));
    }

    Files.createDirectories(storageDir);
    Files.write(participantListPath(payload.getEventTermId()), renderPdf(lines));
    bookletEventProducer.publishParticipantListPdfGenerated(
        new ParticipantListPdfGeneratedPayload(
            payload.getEventTermId(),
            payload.getCaregiverEmail(),
            payload.getEventName(),
            payload.getTermDate()));
  }

  public byte[] readParticipantListPdf(UUID eventTermId) throws IOException {
    return Files.readAllBytes(participantListPath(eventTermId));
  }

  // ── PDF rendering ─────────────────────────────────────────────────────────

  private byte[] renderPdf(List<String> lines) throws IOException {
    PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    int fontSize = 11;
    float lineHeight = 16f;
    float topMargin = 750f;
    float leftMargin = 50f;
    float bottomMargin = 50f;

    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      PDPageContentStream cs = new PDPageContentStream(document, page);
      float y = topMargin;

      for (String line : lines) {
        if (y < bottomMargin) {
          cs.close();
          page = new PDPage(PDRectangle.A4);
          document.addPage(page);
          cs = new PDPageContentStream(document, page);
          y = topMargin;
        }

        boolean isHeader = line.startsWith("===") || line.startsWith("---");
        PDType1Font currentFont = isHeader ? bold : font;

        cs.beginText();
        cs.setFont(currentFont, fontSize);
        cs.newLineAtOffset(leftMargin, y);
        cs.showText(safePdfText(line));
        cs.endText();
        y -= lineHeight;
      }

      cs.close();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private String or(String value, String fallback) {
    return (value == null || value.isBlank()) ? fallback : value;
  }

  private String formatMoney(BigDecimal amount) {
    return amount != null ? amount.toPlainString() + " EUR" : "-";
  }

  private String fmt(LocalDateTime dt) {
    return dt != null ? dt.format(DATE_FMT) : "-";
  }

  private String safePdfText(String text) {
    if (text == null) return "";
    return text.replace('\n', ' ')
        .replace('\r', ' ')
        .replace("–", "-")
        .replace("•", "-")
        .replace("‘", "'")
        .replace("’", "'")
        .replace("“", "\"")
        .replace("”", "\"");
  }

  private Path participantListPath(UUID eventTermId) {
    return storageDir.resolve(eventTermId + ".pdf").normalize();
  }

  private record EventWithTerms(EventDto event, List<EventTermDto> terms) {}

  private record TermEntry(EventDto event, EventTermDto term) {}
}
