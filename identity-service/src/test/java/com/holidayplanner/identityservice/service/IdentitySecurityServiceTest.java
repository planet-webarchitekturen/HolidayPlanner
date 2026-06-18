package com.holidayplanner.identityservice.service;

import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Tests for IdentitySecurityService.
 * 
 * Verifies ownership checks for family member access and modifications.
 * Ensures User A cannot read/modify User B's family members.
 */
@ExtendWith(MockitoExtension.class)
class IdentitySecurityServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    private IdentitySecurityService identitySecurityService;
    private UUID userId1;
    private UUID userId2;
    private UUID memberId;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        identitySecurityService = new IdentitySecurityService(familyMemberRepository);
        userId1 = UUID.randomUUID();
        userId2 = UUID.randomUUID();
        memberId = UUID.randomUUID();

        // Mock authentication for userId1
        auth = new Authentication() {
            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"));
            }

            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getDetails() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return userId1.toString();
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }

            @Override
            public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
            }

            @Override
            public String getName() {
                return userId1.toString();
            }
        };
    }

    @Test
    void testOwnerCanAccessFamilyMember() {
        // Arrange
        FamilyMember member = new FamilyMember();
        member.setId(memberId);
        member.setUserId(userId1);

        when(familyMemberRepository.findById(memberId)).thenReturn(Optional.of(member));

        // Act
        boolean canAccess = identitySecurityService.isFamilyMemberOwner(memberId, auth);

        // Assert
        assertTrue(canAccess, "Owner should be able to access their family member");
    }

    @Test
    void testNonOwnerCannotAccessFamilyMember() {
        // Arrange
        FamilyMember member = new FamilyMember();
        member.setId(memberId);
        member.setUserId(userId2); // Different owner

        when(familyMemberRepository.findById(memberId)).thenReturn(Optional.of(member));

        // Act
        boolean canAccess = identitySecurityService.isFamilyMemberOwner(memberId, auth);

        // Assert
        assertFalse(canAccess, "Non-owner should not be able to access other user's family member");
    }

    @Test
    void testNonExistentMemberReturnsCannotAccess() {
        // Arrange
        when(familyMemberRepository.findById(memberId)).thenReturn(Optional.empty());

        // Act
        boolean canAccess = identitySecurityService.isFamilyMemberOwner(memberId, auth);

        // Assert
        assertFalse(canAccess, "Access to non-existent member should be denied");
    }

    @Test
    void testSelfAccessCheckWithOwnUserId() {
        // Act
        boolean isSelf = identitySecurityService.isSelf(userId1, auth);

        // Assert
        assertTrue(isSelf, "User should be able to access their own profile");
    }

    @Test
    void testSelfAccessCheckWithDifferentUserId() {
        // Act
        boolean isSelf = identitySecurityService.isSelf(userId2, auth);

        // Assert
        assertFalse(isSelf, "User should not be able to access another user's profile without ADMIN role");
    }
}
