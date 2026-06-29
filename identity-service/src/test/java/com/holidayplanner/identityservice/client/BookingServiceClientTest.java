package com.holidayplanner.identityservice.client;

import com.holidayplanner.identityservice.exception.ActiveBookingVetoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BookingServiceClientTest {

    private MockRestServiceServer server;
    private BookingServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BookingServiceClient(builder, "http://booking-service", "service-secret");
    }

    @Test
    void getActiveBookingCountReadsHasActiveContract() {
        UUID memberId = UUID.randomUUID();
        server.expect(requestTo("http://booking-service/api/bookings/family-member/" + memberId + "/has-active"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Service-Secret", "service-secret"))
                .andRespond(withSuccess("""
                        {"hasActiveBookings":true,"activeBookingCount":2}
                        """, MediaType.APPLICATION_JSON));

        long count = client.getActiveBookingCount(memberId);

        assertThat(count).isEqualTo(2L);
        server.verify();
    }

    @Test
    void getActiveBookingCountFailsSafeWhenBookingServiceErrors() {
        UUID memberId = UUID.randomUUID();
        server.expect(requestTo("http://booking-service/api/bookings/family-member/" + memberId + "/has-active"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getActiveBookingCount(memberId))
                .isInstanceOf(ActiveBookingVetoException.class)
                .hasMessageContaining("Cannot verify active bookings");

        server.verify();
    }
}
