package com.holidayplanner.organizationservice.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Mints HS256 JWTs for MockMvc tests, signed with the same default secret the services use in the
 * test profile ({@code jwt.secret} in application-test.yml). Claims match what
 * {@code JwtAuthenticationFilter} reads: subject = userId, organizationId, email, roles.
 */
public final class TestJwt {

    private static final String SECRET = "holidayplanner-super-secret-key-that-is-at-least-256-bits-long";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private TestJwt() {
    }

    /** Token for a random user/organization with the given role(s). */
    public static String token(String... roles) {
        return token(UUID.randomUUID(), UUID.randomUUID(), "test@example.test", roles);
    }

    public static String token(UUID userId, UUID organizationId, String email, String... roles) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("organizationId", organizationId != null ? organizationId.toString() : null)
                .claim("email", email)
                .claim("roles", List.of(roles))
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000))
                .signWith(KEY)
                .compact();
    }
}
