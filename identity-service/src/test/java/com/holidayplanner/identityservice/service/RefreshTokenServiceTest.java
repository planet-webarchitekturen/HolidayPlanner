package com.holidayplanner.identityservice.service;

import com.holidayplanner.identityservice.model.RefreshToken;
import com.holidayplanner.identityservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(RefreshTokenRepository.class);
        service = new RefreshTokenService(repository);
    }

    @Test
    void createRefreshToken_createsTokenWithExpiryAndUser() {
        UUID userId = UUID.randomUUID();
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken token = service.createRefreshToken(userId);

        assertNotNull(token);
        assertNotNull(token.getToken());
        assertEquals(userId, token.getUserId());
        assertFalse(token.isRevoked());
        assertTrue(token.getExpiryDate().isAfter(Instant.now()));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, times(1)).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
    }

    @Test
    void rotate_revokesOldAndCreatesNew() {
        UUID userId = UUID.randomUUID();
        RefreshToken old = new RefreshToken();
        old.setId(UUID.randomUUID());
        old.setToken("old-token");
        old.setUserId(userId);
        old.setExpiryDate(Instant.now().plusSeconds(3600));
        old.setRevoked(false);

        // repository.save should return the passed token
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken newToken = service.rotate(old);

        // old should have been saved revoked (first save call)
        assertTrue(old.isRevoked());
        // new token should be created and saved
        assertNotNull(newToken);
        assertNotEquals(old.getToken(), newToken.getToken());
        assertEquals(userId, newToken.getUserId());

        verify(repository, atLeast(2)).save(any(RefreshToken.class));
    }
}