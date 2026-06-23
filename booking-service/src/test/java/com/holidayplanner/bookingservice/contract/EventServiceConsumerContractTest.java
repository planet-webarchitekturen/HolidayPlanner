package com.holidayplanner.bookingservice.contract;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.holidayplanner.bookingservice.client.EventServiceClient;
import com.holidayplanner.bookingservice.dto.EventTermDetailResponse;
import com.holidayplanner.bookingservice.exception.EventServiceException;
import com.holidayplanner.bookingservice.exception.EventTermNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C) Consumer-side contract tests for EventServiceClient.
 *
 * WireMock simulates event-service. These tests verify two things:
 *  1. The client correctly parses the response shapes event-service promises to send.
 *  2. The client maps every error HTTP status to the right exception type,
 *     so BookingService never receives an unexpected exception.
 *
 * Run both provider and consumer tests together to validate the full contract.
 */
class EventServiceConsumerContractTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private EventServiceClient eventServiceClient;

    @BeforeEach
    void setUp() {
        eventServiceClient = new EventServiceClient(
                RestClient.builder(),
                wm.baseUrl()
        );
    }

    // ── Happy-path contract ───────────────────────────────────────────────────

    @Test
    void getEventTerm_whenActiveResponse_parsesAllFields() {
        UUID id = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/events/terms/" + id))
                .willReturn(okJson("""
                        {
                          "id": "%s",
                          "status": "ACTIVE",
                          "maxParticipants": 20,
                          "organizationId": "%s",
                          "minimalAge": 6,
                          "maximalAge": 12
                        }
                        """.formatted(id, organizationId))));

        EventTermDetailResponse details = eventServiceClient.getEventTerm(id);

        assertThat(details.getId()).isEqualTo(id);
        assertThat(details.getStatus()).isEqualTo("ACTIVE");
        assertThat(details.getMaxParticipants()).isEqualTo(20);
        assertThat(details.getOrganizationId()).isEqualTo(organizationId);
        assertThat(details.getMinimalAge()).isEqualTo(6);
        assertThat(details.getMaximalAge()).isEqualTo(12);
    }

    @Test
    void getEventTerm_whenDraftResponse_parsesStatus() {
        UUID id = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/events/terms/" + id))
                .willReturn(okJson("""
                        {
                          "id": "%s",
                          "status": "DRAFT",
                          "maxParticipants": 10
                        }
                        """.formatted(id))));

        EventTermDetailResponse details = eventServiceClient.getEventTerm(id);

        assertThat(details.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void getEventTerm_whenResponseHasExtraFields_stillParsesCorrectly() {
        // event-service may add new fields in future; client must not break
        UUID id = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/events/terms/" + id))
                .willReturn(okJson("""
                        {
                          "id": "%s",
                          "status": "ACTIVE",
                          "maxParticipants": 5,
                          "unknownFutureField": "someValue",
                          "anotherNewField": 42
                        }
                        """.formatted(id))));

        EventTermDetailResponse details = eventServiceClient.getEventTerm(id);

        assertThat(details.getStatus()).isEqualTo("ACTIVE");
        assertThat(details.getMaxParticipants()).isEqualTo(5);
    }

    // ── Error-mapping contract ────────────────────────────────────────────────

    @Test
    void getEventTerm_when404_throwsEventTermNotFoundException() {
        UUID id = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/events/terms/" + id))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> eventServiceClient.getEventTerm(id))
                .isInstanceOf(EventTermNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getEventTerm_when500_throwsEventServiceException() {
        UUID id = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/events/terms/" + id))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> eventServiceClient.getEventTerm(id))
                .isInstanceOf(EventServiceException.class);
    }

    @Test
    void getEventTerm_when503_throwsEventServiceException() {
        UUID id = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/events/terms/" + id))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> eventServiceClient.getEventTerm(id))
                .isInstanceOf(EventServiceException.class);
    }

    @Test
    void getEventTerm_whenConnectionRefused_throwsEventServiceException() {
        // Point client at a port where nothing listens
        EventServiceClient unreachable = new EventServiceClient(
                RestClient.builder(),
                "http://localhost:1"
        );

        assertThatThrownBy(() -> unreachable.getEventTerm(UUID.randomUUID()))
                .isInstanceOf(EventServiceException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void getEventTerm_whenTimeout_throwsEventServiceException() {
        UUID id = UUID.randomUUID();
        // WireMock fixed-delay longer than any reasonable read timeout
        wm.stubFor(get(urlEqualTo("/api/events/terms/" + id))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5_000)));

        // Client with a 500 ms read timeout
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setReadTimeout(500);
        factory.setConnectTimeout(500);

        EventServiceClient slowClient = new EventServiceClient(
                RestClient.builder().requestFactory(factory),
                wm.baseUrl()
        );

        assertThatThrownBy(() -> slowClient.getEventTerm(id))
                .isInstanceOf(EventServiceException.class)
                .hasMessageContaining("unavailable");
    }

    // ── Request shape contract ────────────────────────────────────────────────

    @Test
    void getEventTerm_sendsGetToCorrectPath() {
        UUID id = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/events/terms/" + id))
                .willReturn(okJson("""
                        {"id":"%s","status":"ACTIVE","maxParticipants":10}
                        """.formatted(id))));

        eventServiceClient.getEventTerm(id);

        wm.verify(1, getRequestedFor(WireMock.urlEqualTo("/api/events/terms/" + id)));
    }
}
