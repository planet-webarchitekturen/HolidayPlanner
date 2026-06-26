package com.holidayplanner.organizationservice.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.organizationservice.client.EventServiceClient;
import com.holidayplanner.organizationservice.client.IdentityServiceClient;
import com.holidayplanner.organizationservice.kafka.OrganizationEventProducer;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.organizationservice.support.TestJwt;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Component (MockMvc) test for {@link com.holidayplanner.organizationservice.controller.OrganizationController}.
 * Boots the full Spring context against an in-memory H2 database (test profile) and only mocks the
 * outbound IPC beans (Kafka + REST clients), so the HTTP layer, JWT security and persistence are
 * all exercised end-to-end. Mirrors the pattern in event-service's EventControllerComponentTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationControllerComponentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private OrganizationEventProducer organizationEventProducer;

    @MockBean
    private EventServiceClient eventServiceClient;

    @MockBean
    private IdentityServiceClient identityServiceClient;

    private static final String ADMIN = "Bearer " + TestJwt.token("ADMIN");
    private static final String USER = "Bearer " + TestJwt.token("USER");

    @BeforeEach
    void clean() {
        organizationRepository.deleteAll();
    }

    @Test
    void health_isPublic_returns200() throws Exception {
        mockMvc.perform(get("/api/organizations/health"))
                .andExpect(status().isOk());
    }

    @Test
    void createOrganization_asAdmin_persistsAndReturnsIt() throws Exception {
        mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .param("name", "Gemeinde Bregenz")
                        .param("bankAccount", "AT12 3456 7890 1234 5678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Gemeinde Bregenz"));
    }

    @Test
    void getOrganization_afterCreate_returnsStoredOrganization() throws Exception {
        String body = mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .param("name", "Gemeinde Dornbirn")
                        .param("bankAccount", "AT99 0000 1111 2222 3333"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(new ObjectMapper().readTree(body).get("id").asText());

        mockMvc.perform(get("/api/organizations/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gemeinde Dornbirn"));
    }

    @Test
    void createOrganization_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/organizations")
                        .param("name", "No Auth")
                        .param("bankAccount", "AT00"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createOrganization_asNonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, USER)
                        .param("name", "Forbidden Org")
                        .param("bankAccount", "AT00"))
                .andExpect(status().isForbidden());
    }
}
