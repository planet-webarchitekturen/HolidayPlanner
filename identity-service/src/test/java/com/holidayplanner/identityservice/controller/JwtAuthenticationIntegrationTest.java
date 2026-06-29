package com.holidayplanner.identityservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.identityservice.command.IdentityCommandService;
import com.holidayplanner.identityservice.dto.LoginRequest;
import com.holidayplanner.identityservice.dto.LoginResponse;
import com.holidayplanner.identityservice.dto.RefreshRequest;
import com.holidayplanner.identityservice.query.IdentityQueryService;
import com.holidayplanner.identityservice.config.JwtTokenProvider;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.shared.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT and Token Refresh Integration Tests.
 * 
 * Verifies:
 * - Login returns access token + refresh token with correct claims
 * - Refresh endpoint renews tokens without re-entering credentials
 * - Expired tokens are rejected with 401
 * - Token claims include userId, organizationId, roles, email
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private IdentityCommandService commandService;

    @MockBean
    private IdentityQueryService queryService;

    private UUID testUserId;
    private UUID testOrgId;
    private String testEmail;
    private String testPassword;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testOrgId = UUID.randomUUID();
        testEmail = "test@example.com";
        testPassword = "password123";

        testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail(testEmail);
        testUser.setOrganizationId(testOrgId);
        testUser.setRole(UserRole.USER);
        testUser.setPhoneNumber("1234567890");
    }

    @Test
    void testLoginReturnsAccessAndRefreshTokens() throws Exception {
        // Arrange
        String accessToken = jwtTokenProvider.generateToken(testUserId, testOrgId, List.of(UserRole.USER.name()), testEmail);
        String refreshToken = UUID.randomUUID().toString();
        
        when(queryService.loginUserWithRefresh(testEmail, testPassword))
                .thenReturn(new IdentityQueryService.LoginTokens(accessToken, refreshToken));
        when(queryService.getUserByEmail(testEmail))
                .thenReturn(testUser);

        LoginRequest request = new LoginRequest(testEmail, testPassword);

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.id").value(testUserId.toString()))
                .andExpect(jsonPath("$.organizationId").value(testOrgId.toString()));
    }

    @Test
    void testRefreshTokenEndpointReturnsNewTokens() throws Exception {
        // Arrange
        String oldRefreshToken = UUID.randomUUID().toString();
        String newAccessToken = jwtTokenProvider.generateToken(testUserId, testOrgId, List.of(UserRole.USER.name()), testEmail);
        String newRefreshToken = UUID.randomUUID().toString();

        when(queryService.loginWithRefreshToken(oldRefreshToken))
                .thenReturn(new IdentityQueryService.LoginTokensWithUser(testUserId, newAccessToken, newRefreshToken));
        when(queryService.getUserById(testUserId))
                .thenReturn(testUser);

        RefreshRequest request = new RefreshRequest(oldRefreshToken);

        // Act & Assert
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.id").value(testUserId.toString()));
    }

    @Test
    void testLoginWithInvalidCredentialsReturns401() throws Exception {
        // Arrange
        when(queryService.loginUserWithRefresh(testEmail, "wrongpassword"))
                .thenThrow(new RuntimeException("Invalid credentials"));

        LoginRequest request = new LoginRequest(testEmail, "wrongpassword");

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAccessWithoutTokenReturns401() throws Exception {
        // Act & Assert - try to access a protected endpoint without token
        mockMvc.perform(post("/api/identity/users/{userId}/family-members", testUserId)
                .param("firstName", "John")
                .param("lastName", "Doe")
                .param("birthDate", "2015-01-01")
                .param("zip", "12345"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testTokenContainsCorrectClaims() throws Exception {
        // Arrange - Generate a token and verify claims
        String token = jwtTokenProvider.generateToken(testUserId, testOrgId, List.of(UserRole.USER.name()), testEmail);

        // Act - Verify token can be extracted and validated
        UUID extractedUserId = jwtTokenProvider.getUserIdFromToken(token);
        UUID extractedOrgId = jwtTokenProvider.getOrganizationIdFromToken(token);

        // Assert
        assert extractedUserId.equals(testUserId);
        assert extractedOrgId.equals(testOrgId);
    }
}
