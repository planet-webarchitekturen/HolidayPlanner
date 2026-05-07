package com.holidayplanner.eventservice.contract;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.holidayplanner.eventservice.client.BookingServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link BookingServiceClient} against WireMock stubs for booking-service HTTP contract.
 */
class BookingServiceConsumerContractTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private BookingServiceClient client;

    @BeforeEach
    void setUp() {
        client = new BookingServiceClient(wm.baseUrl());
    }

    @Test
    void getConfirmedBookingCount_parsesLongBody() {
        UUID termId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/bookings/event-term/" + termId + "/count"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("12")));

        assertThat(client.getConfirmedBookingCount(termId)).isEqualTo(12L);
    }

    @Test
    void getParticipantParentEmails_when404_returnsEmpty() {
        UUID termId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/bookings/event-term/" + termId + "/emails"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.getParticipantParentEmails(termId)).isEmpty();
    }

    @Test
    void getParticipantParentEmails_whenJsonArray_parses() {
        UUID termId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/bookings/event-term/" + termId + "/emails"))
                .willReturn(okJson("[\"a@x.com\",\"b@x.com\"]")));

        assertThat(client.getParticipantParentEmails(termId)).containsExactly("a@x.com", "b@x.com");
    }
}
