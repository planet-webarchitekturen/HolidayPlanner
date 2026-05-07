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
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookletService {

    private static final String WEBSITE_URL = "https://holidayplanner.example.com";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrganizationServiceClient organizationServiceClient;
    private final EventServiceClient eventServiceClient;

    public byte[] generateOrganizationBooklet(UUID organizationId) throws IOException {
        OrganizationDto organization = organizationServiceClient.getOrganization(organizationId);
        List<TeamMemberDto> teamMembers = organizationServiceClient.getTeamMembers(organizationId);
        List<SponsorDto> sponsors = organizationServiceClient.getSponsors(organizationId);
        List<EventDto> events = eventServiceClient.getEventsByOrganization(organizationId);

        try (PDDocument document = new PDDocument()) {
            writeIntroPage(document, organization, teamMembers);
            writeEventIndexPage(document, events);
            writeEventDetailsPages(document, events);
            writeSponsorPage(document, sponsors);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            log.info("Composed booklet generated for organization {}", organizationId);
            return out.toByteArray();
        }
    }

    /**
     * Generates a PDF booklet for an organization.
     *
     * @param organizationName name of the organization
     * @param contactInfo      contact details of the org team
     * @param eventSummaries   list of event summaries (title + date + details)
     * @param sponsorNames     list of sponsor names
     * @return PDF as byte array
     */
    public byte[] generateBooklet(String organizationName,
                                  String contactInfo,
                                  List<String> eventSummaries,
                                  List<String> sponsorNames) throws IOException {

        try (PDDocument document = new PDDocument()) {

            // --- Page 1: Introduction ---
            PDPage introPage = new PDPage(PDRectangle.A4);
            document.addPage(introPage);

            try (PDPageContentStream cs = new PDPageContentStream(document, introPage)) {
                PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                cs.beginText();
                cs.setFont(bold, 24);
                cs.newLineAtOffset(60, 760);
                cs.showText("Holiday Planner – " + organizationName);

                cs.setFont(regular, 12);
                cs.newLineAtOffset(0, -40);
                cs.showText("Welcome to the Holiday Planner event booklet.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Browse all events and book online at: https://holidayplanner.example.com");

                cs.newLineAtOffset(0, -40);
                cs.setFont(bold, 14);
                cs.showText("Contact Information");
                cs.setFont(regular, 12);
                cs.newLineAtOffset(0, -20);
                cs.showText(contactInfo);
                cs.endText();
            }

            // --- Page 2: Event Index ---
            PDPage eventPage = new PDPage(PDRectangle.A4);
            document.addPage(eventPage);

            try (PDPageContentStream cs = new PDPageContentStream(document, eventPage)) {
                PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                cs.beginText();
                cs.setFont(bold, 18);
                cs.newLineAtOffset(60, 760);
                cs.showText("Events");

                cs.setFont(regular, 11);
                float yPosition = 720;

                for (String eventSummary : eventSummaries) {
                    if (yPosition < 60) {
                        // TODO: add new page if content overflows
                        break;
                    }
                    cs.newLineAtOffset(0, -25);
                    cs.showText("• " + eventSummary);
                    yPosition -= 25;
                }
                cs.endText();
            }

            // --- Page 3: Sponsors ---
            PDPage sponsorPage = new PDPage(PDRectangle.A4);
            document.addPage(sponsorPage);

            try (PDPageContentStream cs = new PDPageContentStream(document, sponsorPage)) {
                PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                cs.beginText();
                cs.setFont(bold, 18);
                cs.newLineAtOffset(60, 760);
                cs.showText("Our Sponsors");

                cs.setFont(regular, 12);
                for (String sponsor : sponsorNames) {
                    cs.newLineAtOffset(0, -25);
                    cs.showText("• " + sponsor);
                }
                cs.endText();
            }

            // Write to byte array
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            log.info("Booklet generated for organization: {}", organizationName);
            return out.toByteArray();
        }
    }

    /**
     * Generates a participant list PDF for a caregiver.
     */
    public byte[] generateParticipantListPdf(String eventName, String termDate,
                                              List<String> participantNames) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                cs.beginText();
                cs.setFont(bold, 18);
                cs.newLineAtOffset(60, 760);
                cs.showText("Participant List");

                cs.setFont(regular, 13);
                cs.newLineAtOffset(0, -30);
                cs.showText("Event: " + eventName);
                cs.newLineAtOffset(0, -20);
                cs.showText("Date: " + termDate);

                cs.setFont(bold, 13);
                cs.newLineAtOffset(0, -35);
                cs.showText("Participants (" + participantNames.size() + "):");

                cs.setFont(regular, 12);
                int count = 1;
                for (String name : participantNames) {
                    cs.newLineAtOffset(0, -22);
                    cs.showText(count++ + ". " + name);
                }
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private void writeIntroPage(PDDocument document, OrganizationDto organization,
                                List<TeamMemberDto> teamMembers) throws IOException {
        PDPage page = addPage(document);
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            TextCursor cursor = new TextCursor(60, 760);

            writeLine(cs, bold, 24, cursor, "Holiday Planner - " + textOrFallback(organization.name(), "Organization"));
            cursor.moveDown(32);
            writeLine(cs, regular, 12, cursor, "This booklet contains all current event terms for the organization.");
            writeLine(cs, regular, 12, cursor, "Online booking and updates: " + WEBSITE_URL);

            cursor.moveDown(20);
            writeLine(cs, bold, 16, cursor, "Organization Contact");
            if (teamMembers.isEmpty()) {
                writeLine(cs, regular, 12, cursor, "No team contact information available.");
            } else {
                for (TeamMemberDto member : teamMembers) {
                    writeLine(cs, regular, 12, cursor, formatTeamMember(member));
                }
            }
        }
    }

    private void writeEventIndexPage(PDDocument document, List<EventDto> events) throws IOException {
        PDPage page = addPage(document);
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            TextCursor cursor = new TextCursor(60, 760);

            writeLine(cs, bold, 18, cursor, "Event Term Index");
            List<EventTermLine> terms = sortedTerms(events);
            if (terms.isEmpty()) {
                writeLine(cs, regular, 12, cursor, "No event terms available.");
                return;
            }
            for (EventTermLine term : terms) {
                if (cursor.needsNewPage()) {
                    break;
                }
                writeLine(cs, regular, 11, cursor,
                        formatDate(term.term().startDateTime()) + " - " + term.event().shortTitle());
            }
        }
    }

    private void writeEventDetailsPages(PDDocument document, List<EventDto> events) throws IOException {
        PDPage page = addPage(document);
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            TextCursor cursor = new TextCursor(60, 760);

            writeLine(cs, bold, 18, cursor, "Events");
            if (events.isEmpty()) {
                writeLine(cs, regular, 12, cursor, "No events available.");
                return;
            }

            for (EventDto event : events) {
                if (cursor.needsNewPage()) {
                    break;
                }
                cursor.moveDown(8);
                writeLine(cs, bold, 14, cursor, textOrFallback(event.shortTitle(), "Untitled event"));
                writeLine(cs, regular, 11, cursor, "Location: " + textOrFallback(event.location(), "-"));
                writeLine(cs, regular, 11, cursor, "Meeting point: " + textOrFallback(event.meetingPoint(), "-"));
                writeLine(cs, regular, 11, cursor, "Price: " + formatMoney(event.price())
                        + " | Payment: " + textOrFallback(event.paymentMethod(), "-"));
                writeLine(cs, regular, 11, cursor, "Age: " + event.minimalAge() + "-" + event.maximalAge());
                writeLine(cs, regular, 11, cursor, "Description: " + textOrFallback(event.description(), "-"));

                List<EventTermDto> terms = safeTerms(event).stream()
                        .sorted(Comparator.comparing(EventTermDto::startDateTime,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
                if (terms.isEmpty()) {
                    writeLine(cs, regular, 11, cursor, "Terms: none");
                } else {
                    writeLine(cs, bold, 11, cursor, "Terms:");
                    for (EventTermDto term : terms) {
                        writeLine(cs, regular, 11, cursor,
                                formatDate(term.startDateTime()) + " to " + formatDate(term.endDateTime())
                                        + " | min/max: " + term.minParticipants() + "/" + term.maxParticipants()
                                        + " | status: " + textOrFallback(term.status(), "-"));
                    }
                }
            }
        }
    }

    private void writeSponsorPage(PDDocument document, List<SponsorDto> sponsors) throws IOException {
        PDPage page = addPage(document);
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            TextCursor cursor = new TextCursor(60, 760);

            writeLine(cs, bold, 18, cursor, "Sponsors");
            if (sponsors.isEmpty()) {
                writeLine(cs, regular, 12, cursor, "No sponsors available.");
                return;
            }
            for (SponsorDto sponsor : sponsors) {
                writeLine(cs, regular, 12, cursor,
                        textOrFallback(sponsor.name(), "Unnamed sponsor") + " - " + formatMoney(sponsor.amount()));
            }
        }
    }

    private PDPage addPage(PDDocument document) {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        return page;
    }

    private void writeLine(PDPageContentStream cs, PDType1Font font, int fontSize,
                           TextCursor cursor, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(cursor.x(), cursor.y());
        cs.showText(safePdfText(text));
        cs.endText();
        cursor.moveDown(fontSize + 8);
    }

    private String formatTeamMember(TeamMemberDto member) {
        return textOrFallback(member.firstName(), "") + " " + textOrFallback(member.lastName(), "")
                + " (" + textOrFallback(member.role(), "team") + ") - "
                + textOrFallback(member.email(), "no email");
    }

    private List<EventTermLine> sortedTerms(List<EventDto> events) {
        return events.stream()
                .flatMap(event -> safeTerms(event).stream().map(term -> new EventTermLine(event, term)))
                .sorted(Comparator.comparing(line -> line.term().startDateTime(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<EventTermDto> safeTerms(EventDto event) {
        return event.terms() != null ? event.terms() : List.of();
    }

    private String formatDate(java.time.LocalDateTime dateTime) {
        return dateTime != null ? DATE_TIME_FORMAT.format(dateTime) : "-";
    }

    private String formatMoney(BigDecimal amount) {
        return amount != null ? amount + " EUR" : "-";
    }

    private String textOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safePdfText(String text) {
        return Stream.of(textOrFallback(text, ""))
                .map(value -> value.replace('\n', ' ').replace('\r', ' '))
                .map(value -> value.replace("–", "-").replace("•", "-"))
                .findFirst()
                .orElse("");
    }

    private record EventTermLine(EventDto event, EventTermDto term) {
    }

    private static final class TextCursor {
        private static final float MIN_Y = 70;
        private final float x;
        private float y;

        private TextCursor(float x, float y) {
            this.x = x;
            this.y = y;
        }

        private float x() {
            return x;
        }

        private float y() {
            return y;
        }

        private void moveDown(float amount) {
            y -= amount;
        }

        private boolean needsNewPage() {
            return y < MIN_Y;
        }
    }
}
