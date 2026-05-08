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

    public static UUID getCurrentOrganizationId() {
        JwtClaims claims = getCurrentUser();
        return claims != null ? claims.getOrganizationId() : null;
    }

    public static boolean hasRole(String role) {
        JwtClaims claims = getCurrentUser();
        return claims != null && claims.hasRole(role);
    }
}
