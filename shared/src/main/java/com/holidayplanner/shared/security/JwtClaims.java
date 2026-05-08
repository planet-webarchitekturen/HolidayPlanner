package com.holidayplanner.shared.security;

import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
public class JwtClaims {

    private final UUID userId;
    private final UUID organizationId;
    private final List<String> roles;
    private final String email;

    public JwtClaims(UUID userId, UUID organizationId, List<String> roles, String email) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.roles = roles;
        this.email = email;
    }

    public static JwtClaims fromAuthentication(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof JwtClaims) {
            return (JwtClaims) auth.getPrincipal();
        }
        // Fallback: reconstruct from authorities when principal is a String (userId)
        String userId = auth.getPrincipal().toString();
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .collect(Collectors.toList());
        return new JwtClaims(UUID.fromString(userId), null, roles, null);
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
