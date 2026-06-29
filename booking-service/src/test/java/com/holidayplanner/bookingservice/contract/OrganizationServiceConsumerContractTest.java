package com.holidayplanner.bookingservice.contract;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.holidayplanner.bookingservice.client.OrganizationServiceClient;
import com.holidayplanner.bookingservice.dto.OrganizationResponse;
import com.holidayplanner.bookingservice.exception.OrganizationServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationServiceConsumerContractTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private OrganizationServiceClient organizationServiceClient;

    @BeforeEach
    void setUp() {
        organizationServiceClient = new OrganizationServiceClient(
                RestClient.builder(),
                wm.baseUrl(),
                "holidayplanner-internal-service-secret"
        );
    }

    @Test
    void getOrganization_whenResponseContainsBookingStartTime_parsesStoryOneFields() {
        UUID organizationId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/organizations/" + organizationId))
                .willReturn(okJson("""
                        {
                          "id": "%s",
                          "name": "Holiday Helpers",
                          "bankAccount": "AT123",
                          "bookingStartTime": "2026-06-24T10:15:30"
                        }
                        """.formatted(organizationId))));

        OrganizationResponse response = organizationServiceClient.getOrganization(organizationId);

        assertThat(response.getId()).isEqualTo(organizationId);
        assertThat(response.getBookingStartTime()).isEqualTo(LocalDateTime.of(2026, 6, 24, 10, 15, 30));
    }

    @Test
    void getOrganization_sendsGetToCorrectPathAndServiceSecret() {
        UUID organizationId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/organizations/" + organizationId))
                .willReturn(okJson("""
                        {"id":"%s","bookingStartTime":"2026-06-24T10:15:30"}
                        """.formatted(organizationId))));

        organizationServiceClient.getOrganization(organizationId);

        wm.verify(1, getRequestedFor(urlEqualTo("/api/organizations/" + organizationId))
                .withHeader("X-Service-Secret", equalTo("holidayplanner-internal-service-secret")));
    }

    @Test
    void getOrganization_when4xx_throwsIllegalStateException() {
        UUID organizationId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/organizations/" + organizationId))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> organizationServiceClient.getOrganization(organizationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Organization not found");
    }

    @Test
    void getOrganization_when5xx_throwsOrganizationServiceException() {
        UUID organizationId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/organizations/" + organizationId))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> organizationServiceClient.getOrganization(organizationId))
                .isInstanceOf(OrganizationServiceException.class)
                .hasMessageContaining("server error");
    }

    @Test
    void getOrganization_whenConnectionRefused_throwsOrganizationServiceException() {
        OrganizationServiceClient unreachable = new OrganizationServiceClient(
                RestClient.builder(),
                "http://localhost:1",
                "holidayplanner-internal-service-secret"
        );

        assertThatThrownBy(() -> unreachable.getOrganization(UUID.randomUUID()))
                .isInstanceOf(OrganizationServiceException.class)
                .hasMessageContaining("unavailable");
    }
}
