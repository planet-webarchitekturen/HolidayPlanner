package com.holidayplanner.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class SecurityUtils {

    private SecurityUtils() {}

    public static JwtClaims getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return JwtClaims.fromAuthentication(auth);
    }

    public static UUID getCurrentUserId() {
        JwtClaims claims = getCurrentUser();
        return claims != null ? claims.getUserId() : null;
    }

    private static final UUID NIL_UUID = new UUID(0, 0);

    public static UUID getCurrentOrganizationId() {
        JwtClaims claims = getCurrentUser();
        if (claims == null) return null;
        UUID orgId = claims.getOrganizationId();
        // Nil UUID (all zeros) means user has no organization — treat as null
        return (orgId == null || NIL_UUID.equals(orgId)) ? null : orgId;
    }

    public static boolean hasRole(String role) {
        JwtClaims claims = getCurrentUser();
        return claims != null && claims.hasRole(role);
    }
}
