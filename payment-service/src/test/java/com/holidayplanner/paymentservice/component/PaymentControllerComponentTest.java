package com.holidayplanner.paymentservice.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.paymentservice.client.BookingServiceClient;
import com.holidayplanner.paymentservice.client.EventServiceClient;
import com.holidayplanner.paymentservice.kafka.PaymentEventProducer;
import com.holidayplanner.paymentservice.repository.PaymentRepository;
import com.holidayplanner.paymentservice.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Component (MockMvc) test for {@link com.holidayplanner.paymentservice.controller.PaymentController}.
 * Boots the full Spring context against an in-memory H2 database (test profile) and only mocks the
 * outbound IPC beans (Kafka + REST clients). Exercises the HTTP layer, JWT security (org-scoped
 * accountant actions) and the PENDING → PAID → REFUNDED state machine end-to-end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerComponentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private PaymentEventProducer paymentEventProducer;

    @MockBean
    private EventServiceClient eventServiceClient;

    @MockBean
    private BookingServiceClient bookingServiceClient;

    /** Accountant's JWT and the payments it operates on must share the same organization (org-scoped checks). */
    private static final UUID ORG = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ACCOUNTANT =
            "Bearer " + TestJwt.token(UUID.randomUUID(), ORG, "accountant@example.test", "ACCOUNTANT");

    @BeforeEach
    void clean() {
        paymentRepository.deleteAll();
    }

    @Test
    void health_isPublic_returns200() throws Exception {
        mockMvc.perform(get("/api/payments/health"))
                .andExpect(status().isOk());
    }

    @Test
    void createPayment_asAccountant_returnsPending() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header(HttpHeaders.AUTHORIZATION, ACCOUNTANT)
                        .param("bookingId", UUID.randomUUID().toString())
                        .param("organizationId", ORG.toString())
                        .param("amount", "30.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void payThenRefund_movesPaymentThroughStateMachine() throws Exception {
        UUID paymentId = createPayment();

        mockMvc.perform(patch("/api/payments/{id}/pay", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, ACCOUNTANT)
                        .param("note", "Received via bank transfer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(patch("/api/payments/{id}/refund", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, ACCOUNTANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void balanceEndpoint_isAccessible() throws Exception {
        createPayment();
        mockMvc.perform(get("/api/payments/organization/{id}/balance", ORG)
                        .header(HttpHeaders.AUTHORIZATION, ACCOUNTANT))
                .andExpect(status().isOk());
    }

    @Test
    void createPayment_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .param("bookingId", UUID.randomUUID().toString())
                        .param("organizationId", ORG.toString())
                        .param("amount", "30.00"))
                .andExpect(status().isUnauthorized());
    }

    private UUID createPayment() throws Exception {
        String body = mockMvc.perform(post("/api/payments")
                        .header(HttpHeaders.AUTHORIZATION, ACCOUNTANT)
                        .param("bookingId", UUID.randomUUID().toString())
                        .param("organizationId", ORG.toString())
                        .param("amount", "30.00"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(new ObjectMapper().readTree(body).get("id").asText());
    }
}
