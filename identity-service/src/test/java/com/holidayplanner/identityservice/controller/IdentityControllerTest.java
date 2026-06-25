package com.holidayplanner.identityservice.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.holidayplanner.identityservice.command.IdentityCommandService;
import com.holidayplanner.identityservice.query.IdentityQueryService;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.shared.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMVC tests for {@link IdentityController}.
 *
 * Uses standalone setup so the test focuses on the web layer — request mapping,
 * parameter binding, and the entity → response-DTO mapping — with the services
 * mocked. Method-level security (@PreAuthorize) is exercised separately; it is
 * not evaluated under standalone setup.
 */
@ExtendWith(MockitoExtension.class)
class IdentityControllerTest {

    @Mock private IdentityCommandService commandService;
    @Mock private IdentityQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var objectMapper = Jackson2ObjectMapperBuilder.json()
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(new IdentityController(commandService, queryService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void registerReturnsUserResponseWithoutPasswordHash() throws Exception {
        UUID orgId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("bob@example.com");
        user.setPasswordHash("topSecretHash");
        user.setPhoneNumber("222");
        user.setOrganizationId(orgId);
        user.setRole(UserRole.USER);
        when(commandService.registerUser(eq("bob@example.com"), eq("secret"), eq("222"), eq(orgId)))
                .thenReturn(user);

        mockMvc.perform(post("/api/identity/users/register")
                        .param("email", "bob@example.com")
                        .param("password", "secret")
                        .param("phoneNumber", "222")
                        .param("organizationId", orgId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void getUserReturnsUser() throws Exception {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setEmail("alice@example.com");
        user.setPhoneNumber("111");
        user.setOrganizationId(UUID.randomUUID());
        user.setRole(UserRole.USER);
        when(queryService.getUserById(id)).thenReturn(user);

        mockMvc.perform(get("/api/identity/users/{userId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void addFamilyMemberReturnsFlattenedResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        User owner = new User();
        owner.setId(userId);

        FamilyMember saved = new FamilyMember();
        saved.setId(UUID.randomUUID());
        saved.setUser(owner);
        saved.setFirstName("Kid");
        saved.setLastName("Smith");
        saved.setBirthDate(LocalDate.of(2015, 1, 1));
        saved.setZip("12345");
        when(commandService.addFamilyMember(eq(userId), any(), any(), any(), any())).thenReturn(saved);

        mockMvc.perform(post("/api/identity/users/{userId}/family-members", userId)
                        .param("firstName", "Kid")
                        .param("lastName", "Smith")
                        .param("birthDate", "2015-01-01")
                        .param("zip", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Kid"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void getFamilyMemberReturnsBirthDateForBookingAgeContract() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        User owner = new User();
        owner.setId(userId);

        FamilyMember member = new FamilyMember();
        member.setId(memberId);
        member.setUser(owner);
        member.setFirstName("Kid");
        member.setLastName("Smith");
        member.setBirthDate(LocalDate.of(2018, 4, 12));
        member.setZip("1010");
        when(queryService.getFamilyMemberById(memberId)).thenReturn(member);

        mockMvc.perform(get("/api/identity/family-members/{memberId}", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.birthDate").value("2018-04-12"));
    }

    @Test
    void getFamilyMemberOwnerEmailReturnsEmailForBookingCreatedPayload() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(queryService.getUserEmailByFamilyMemberId(memberId)).thenReturn("parent@example.com");

        mockMvc.perform(get("/api/identity/family-members/{memberId}/owner-email", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("parent@example.com"));
    }

    @Test
    void ownerEmailEndpointAllowsFamilyMemberOwnerForBookingCreatedPayloadEnrichment() throws Exception {
        PreAuthorize preAuthorize = IdentityController.class
                .getMethod("getFamilyMemberOwnerEmail", UUID.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value())
                .contains("@identitySecurity.isFamilyMemberOwner(#memberId, authentication)")
                .contains("ORGANIZATION_TEAM_MEMBER")
                .contains("ADMIN")
                .contains("EVENT_OWNER");
    }
}
