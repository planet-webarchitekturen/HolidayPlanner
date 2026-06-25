package com.holidayplanner.bookingservice.contract;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.holidayplanner.bookingservice.client.IdentityServiceClient;
import com.holidayplanner.bookingservice.dto.FamilyMemberResponse;
import com.holidayplanner.bookingservice.exception.IdentityServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityServiceConsumerContractTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private IdentityServiceClient identityServiceClient;

    @BeforeEach
    void setUp() {
        identityServiceClient = new IdentityServiceClient(
                RestClient.builder(),
                "test-service-secret",
                wm.baseUrl()
        );
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getFamilyMember_whenResponseContainsBirthDate_parsesStoryOneFields() {
        UUID memberId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/identity/family-members/" + memberId))
                .willReturn(okJson("""
                        {
                          "id": "%s",
                          "userId": "%s",
                          "firstName": "Kid",
                          "lastName": "Smith",
                          "birthDate": "2018-04-12",
                          "zip": "1010"
                        }
                        """.formatted(memberId, UUID.randomUUID()))));

        FamilyMemberResponse response = identityServiceClient.getFamilyMember(memberId);

        assertThat(response.getId()).isEqualTo(memberId);
        assertThat(response.getBirthDate()).isEqualTo(LocalDate.of(2018, 4, 12));
    }

    @Test
    void getFamilyMember_sendsGetToCorrectPathAndForwardsBearerToken() {
        UUID memberId = UUID.randomUUID();
        withBearerToken("booking-token");
        wm.stubFor(get(urlEqualTo("/api/identity/family-members/" + memberId))
                .willReturn(okJson("""
                        {"id":"%s","birthDate":"2016-01-01"}
                        """.formatted(memberId))));

        identityServiceClient.getFamilyMember(memberId);

        wm.verify(1, getRequestedFor(urlEqualTo("/api/identity/family-members/" + memberId))
                .withHeader("Authorization", WireMock.equalTo("Bearer booking-token")));
    }

    @Test
    void getFamilyMember_when4xx_throwsIllegalArgumentException() {
        UUID memberId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/identity/family-members/" + memberId))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> identityServiceClient.getFamilyMember(memberId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Family member not found");
    }

    @Test
    void getFamilyMember_when5xx_throwsIdentityServiceException() {
        UUID memberId = UUID.randomUUID();
        wm.stubFor(get(urlEqualTo("/api/identity/family-members/" + memberId))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> identityServiceClient.getFamilyMember(memberId))
                .isInstanceOf(IdentityServiceException.class)
                .hasMessageContaining("server error");
    }

    @Test
    void getFamilyMember_whenConnectionRefused_throwsIdentityServiceException() {
        IdentityServiceClient unreachable = new IdentityServiceClient(
                RestClient.builder(),
                "test-service-secret",
                "http://localhost:1"
        );

        assertThatThrownBy(() -> unreachable.getFamilyMember(UUID.randomUUID()))
                .isInstanceOf(IdentityServiceException.class)
                .hasMessageContaining("unavailable");
    }

    private void withBearerToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
