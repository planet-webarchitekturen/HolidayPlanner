package com.holidayplanner.bookletservice.service;

import com.holidayplanner.bookletservice.client.EventDto;
import com.holidayplanner.bookletservice.client.EventServiceClient;
import com.holidayplanner.bookletservice.client.EventTermDto;
import com.holidayplanner.bookletservice.client.OrganizationDto;
import com.holidayplanner.bookletservice.client.OrganizationServiceClient;
import com.holidayplanner.bookletservice.client.SponsorDto;
import com.holidayplanner.bookletservice.client.TeamMemberDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookletService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final OrganizationServiceClient organizationServiceClient;
    private final EventServiceClient eventServiceClient;

    public byte[] generateOrganizationBooklet(UUID organizationId) throws IOException {
        OrganizationDto organization = organizationServiceClient.getOrganization(organizationId);
        List<TeamMemberDto> teamMembers = organizationServiceClient.getTeamMembers(organizationId);
        List<SponsorDto> sponsors = organizationServiceClient.getSponsors(organizationId);
        List<EventDto> events = eventServiceClient.getEventsByOrganization(organizationId);

        List<String> lines = new ArrayList<>();

        // Organization
        lines.add("=== " + or(organization.name(), "Organization") + " ===");
        lines.add("");
        if (organization.bookingStartTime() != null) {
            lines.add("Bookings open: " + organization.bookingStartTime().format(DATE_FMT));
        }
        lines.add("");

        // Team
        lines.add("--- Team ---");
        if (teamMembers.isEmpty()) {
            lines.add("No team members.");
        } else {
            for (TeamMemberDto m : teamMembers) {
                lines.add(or(m.firstName(), "") + " " + or(m.lastName(), "")
                        + " | " + or(m.role(), "") + " | " + or(m.email(), ""));
            }
        }
        lines.add("");

        // Events
        lines.add("--- Events ---");
        if (events.isEmpty()) {
            lines.add("No events.");
        }
        for (EventDto event : events) {
            lines.add("");
            lines.add(or(event.shortTitle(), "Untitled"));
            lines.add("Location: " + or(event.location(), "-") + " | Meeting point: " + or(event.meetingPoint(), "-"));
            lines.add("Price: " + formatMoney(event.price()) + " | Payment: " + or(event.paymentMethod(), "-"));
            lines.add("Age: " + event.minimalAge() + "-" + event.maximalAge());
            lines.add("Description: " + or(event.description(), "-"));

            List<EventTermDto> terms = event.terms() != null ? event.terms() : List.of();
            terms = terms.stream()
                    .sorted(Comparator.comparing(EventTermDto::startDateTime,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            if (terms.isEmpty()) {
                lines.add("Terms: none");
            } else {
                for (EventTermDto t : terms) {
                    lines.add("  Term: " + fmt(t.startDateTime()) + " - " + fmt(t.endDateTime())
                            + " | " + t.minParticipants() + "-" + t.maxParticipants() + " participants"
                            + " | " + or(t.status(), "-"));
                }
            }
        }
        lines.add("");

        // Sponsors
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

    public byte[] generateParticipantListPdf(String eventName, String termDate,
                                             List<String> participantNames) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("Participant List");
        lines.add("");
        lines.add("Event: " + or(eventName, "-"));
        lines.add("Date:  " + or(termDate, "-"));
        lines.add("Total: " + participantNames.size());
        lines.add("");
        int i = 1;
        for (String name : participantNames) {
            lines.add(i++ + ". " + or(name, "-"));
        }
        return renderPdf(lines);
    }

    public byte[] generateBooklet(String organizationName, String contactInfo,
                                  List<String> eventSummaries, List<String> sponsorNames) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("Holiday Planner - " + or(organizationName, "Organization"));
        lines.add("");
        lines.add("Contact: " + or(contactInfo, "-"));
        lines.add("");
        lines.add("--- Events ---");
        eventSummaries.forEach(lines::add);
        lines.add("");
        lines.add("--- Sponsors ---");
        sponsorNames.forEach(lines::add);
        return renderPdf(lines);
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
        return text.replace('\n', ' ').replace('\r', ' ')
                   .replace("–", "-").replace("•", "-")
                   .replace("‘", "'").replace("’", "'")
                   .replace("“", "\"").replace("”", "\"");
    }
}
