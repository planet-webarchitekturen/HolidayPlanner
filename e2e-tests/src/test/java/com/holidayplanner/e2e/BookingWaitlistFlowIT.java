package com.holidayplanner.e2e;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingWaitlistFlowIT {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String JWT_SECRET = System.getProperty(
            "e2e.jwtSecret",
            "holidayplanner-super-secret-key-that-is-at-least-256-bits-long");
    private static final String BASE_URL = System.getProperty("e2e.baseUrl", "http://127.0.0.1");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void bookingWaitlistPromotionFlowWorksAcrossServices() {
        waitForHealth("event-service", 8081, "/api/events/health");
        waitForHealth("booking-service", 8082, "/api/bookings/health");
        waitForHealth("identity-service", 8083, "/api/identity/health");
        waitForHealth("organization-service", 8084, "/api/organizations/health");
        waitForHealth("payment-service", 8085, "/api/payments/health");

        String runId = Long.toString(System.currentTimeMillis());
        String bootstrapAdminToken = jwt(UUID.randomUUID(), UUID.randomUUID(), "admin-" + runId + "@demo.test", "ADMIN");

        JsonNode organization = postForm(
                8084,
                "/api/organizations",
                bootstrapAdminToken,
                Map.of(
                        "name", "E2E Demo Municipality " + runId,
                        "bankAccount", "AT611904300234573201",
                        "bookingStartTime", LocalDateTime.now().minusDays(1).toString()));
        UUID organizationId = uuid(organization, "id");
        String adminToken = jwt(UUID.randomUUID(), organizationId, "admin-" + runId + "@demo.test", "ADMIN");

        JsonNode user = postForm(
                8083,
                "/api/auth/register",
                null,
                Map.of(
                        "email", "parent-" + runId + "@demo.test",
                        "password", "Password123!",
                        "phoneNumber", "+430000000",
                        "organizationId", organizationId.toString()));
        UUID userId = uuid(user, "id");

        JsonNode login = postJson(
                8083,
                "/api/auth/login",
                null,
                Map.of("email", "parent-" + runId + "@demo.test", "password", "Password123!"));
        String userToken = login.get("token").asText();

        JsonNode anna = postForm(
                8083,
                "/api/identity/users/" + userId + "/family-members",
                userToken,
                Map.of(
                        "firstName", "Anna",
                        "lastName", "Demo",
                        "birthDate", LocalDate.now().minusYears(10).toString(),
                        "zip", "6900"));
        UUID annaId = uuid(anna, "id");

        JsonNode ben = postForm(
                8083,
                "/api/identity/users/" + userId + "/family-members",
                userToken,
                Map.of(
                        "firstName", "Ben",
                        "lastName", "Demo",
                        "birthDate", LocalDate.now().minusYears(9).toString(),
                        "zip", "6900"));
        UUID benId = uuid(ben, "id");

        JsonNode event = postJson(
                8081,
                "/api/events",
                adminToken,
                Map.ofEntries(
                        entry("organizationId", organizationId.toString()),
                        entry("eventOwnerId", userId.toString()),
                        entry("shortTitle", "Bike Adventure " + runId),
                        entry("description", "E2E waitlist demo event"),
                        entry("location", "Demo Park"),
                        entry("meetingPoint", "Main gate"),
                        entry("price", new BigDecimal("15.00")),
                        entry("paymentMethod", "BANK_TRANSFER"),
                        entry("minimalAge", 6),
                        entry("maximalAge", 16),
                        entry("pictureUrl", "")));
        UUID eventId = uuid(event, "id");

        JsonNode term = postJson(
                8081,
                "/api/events/" + eventId + "/terms",
                adminToken,
                Map.of(
                        "startDateTime", LocalDateTime.now().plusDays(14).withNano(0).toString(),
                        "endDateTime", LocalDateTime.now().plusDays(14).plusHours(3).withNano(0).toString(),
                        "minParticipants", 1,
                        "maxParticipants", 1));
        UUID termId = uuid(term, "id");

        patchJson(8081, "/api/events/terms/" + termId + "/status", adminToken, Map.of("newStatus", "ACTIVE"));

        JsonNode annaBooking = postForm(
                8082,
                "/api/bookings",
                userToken,
                Map.of("familyMemberId", annaId.toString(), "eventTermId", termId.toString()));
        UUID annaBookingId = uuid(annaBooking, "id");
        assertEquals("CONFIRMED", annaBooking.get("status").asText(), "first booking must consume only seat");

        JsonNode annaPayment = eventually("payment for first confirmed booking", () ->
                getJsonOrNull(8085, "/api/payments/booking/" + annaBookingId, adminToken));
        assertEquals("PENDING", annaPayment.get("status").asText(), "confirmed booking should create pending payment");

        JsonNode benBooking = postForm(
                8082,
                "/api/bookings",
                userToken,
                Map.of("familyMemberId", benId.toString(), "eventTermId", termId.toString()));
        UUID benBookingId = uuid(benBooking, "id");
        assertEquals("WAITLISTED", benBooking.get("status").asText(), "second booking must enter waiting list");

        delete(8082, "/api/bookings/" + annaBookingId, userToken);

        JsonNode promotedBen = eventually("Ben promoted from waitlist", () -> {
            JsonNode booking = getJsonOrNull(8082, "/api/bookings/" + benBookingId, userToken);
            return booking != null && "CONFIRMED".equals(booking.get("status").asText()) ? booking : null;
        });

        assertEquals("CONFIRMED", promotedBen.get("status").asText(), "waitlisted booking should be promoted");

        List<JsonNode> bookings = getList(8082, "/api/bookings/event-term/" + termId, userToken);
        assertTrue(bookings.stream().anyMatch(b ->
                annaBookingId.toString().equals(b.get("id").asText()) && "CANCELLED".equals(b.get("status").asText())));
        assertTrue(bookings.stream().anyMatch(b ->
                benBookingId.toString().equals(b.get("id").asText()) && "CONFIRMED".equals(b.get("status").asText())));
    }

    private static void waitForHealth(String service, int port, String path) {
        eventually(service + " health", () -> {
            try {
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(uri(port, path))
                                .timeout(Duration.ofSeconds(3))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200 ? response.body() : null;
            } catch (Exception ignored) {
                return null;
            }
        });
    }

    private static JsonNode postForm(int port, String path, String bearerToken, Map<String, String> params) {
        return sendJson("POST", port, path, bearerToken, formBody(params), "application/x-www-form-urlencoded");
    }

    private static JsonNode postJson(int port, String path, String bearerToken, Object body) {
        return sendJson("POST", port, path, bearerToken, jsonBody(body), "application/json");
    }

    private static JsonNode patchJson(int port, String path, String bearerToken, Object body) {
        return sendJson("PATCH", port, path, bearerToken, jsonBody(body), "application/json");
    }

    private static JsonNode getJsonOrNull(int port, String path, String bearerToken) {
        try {
            HttpResponse<String> response = send("GET", port, path, bearerToken, "", null);
            return response.statusCode() == 200 ? JSON.readTree(response.body()) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<JsonNode> getList(int port, String path, String bearerToken) {
        try {
            HttpResponse<String> response = send("GET", port, path, bearerToken, "", null);
            assertStatus(200, response, "GET " + path);
            return JSON.readValue(response.body(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new AssertionError("Could not parse list response from " + path, e);
        }
    }

    private static JsonNode delete(int port, String path, String bearerToken) {
        return sendJson("DELETE", port, path, bearerToken, "", null);
    }

    private static JsonNode sendJson(
            String method,
            int port,
            String path,
            String bearerToken,
            String body,
            String contentType) {
        try {
            HttpResponse<String> response = send(method, port, path, bearerToken, body, contentType);
            assertStatus(response.statusCode() >= 200 && response.statusCode() < 300, response, method + " " + path);
            return response.body() == null || response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
        } catch (IOException e) {
            throw new AssertionError("Could not parse response from " + method + " " + path, e);
        }
    }

    private static HttpResponse<String> send(
            String method,
            int port,
            String path,
            String bearerToken,
            String body,
            String contentType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri(port, path))
                    .timeout(Duration.ofSeconds(10));
            if (bearerToken != null) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            if (contentType != null) {
                builder.header("Content-Type", contentType);
            }
            if ("GET".equals(method)) {
                builder.GET();
            } else if ("DELETE".equals(method)) {
                builder.DELETE();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AssertionError("HTTP request failed: " + method + " " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("HTTP request interrupted: " + method + " " + path, e);
        }
    }

    private static URI uri(int port, String path) {
        return URI.create(BASE_URL + ":" + port + path);
    }

    private static String formBody(Map<String, String> params) {
        StringBuilder body = new StringBuilder();
        params.forEach((key, value) -> {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(encode(key)).append('=').append(encode(value));
        });
        return body.toString();
    }

    private static String jsonBody(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (IOException e) {
            throw new AssertionError("Could not serialize JSON body", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static UUID uuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        assertNotNull(value, "Expected field '" + field + "' in response: " + node);
        return UUID.fromString(value.asText());
    }

    private static <T> T eventually(String description, Supplier<T> supplier) {
        AssertionError lastAssertion = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                T value = supplier.get();
                if (value != null) {
                    return value;
                }
            } catch (AssertionError e) {
                lastAssertion = e;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + description, e);
            }
        }
        if (lastAssertion != null) {
            throw lastAssertion;
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static void assertStatus(int expected, HttpResponse<String> response, String action) {
        assertStatus(response.statusCode() == expected, response, action);
    }

    private static void assertStatus(boolean ok, HttpResponse<String> response, String action) {
        assertTrue(ok, () -> action + " returned " + response.statusCode() + " with body: " + response.body());
    }

    private static String jwt(UUID userId, UUID organizationId, String email, String role) {
        String header = base64Url(jsonBody(Map.of("alg", "HS256", "typ", "JWT")));
        String payload = base64Url(jsonBody(Map.of(
                "sub", userId.toString(),
                "organizationId", organizationId.toString(),
                "email", email,
                "roles", List.of(role),
                "iat", System.currentTimeMillis() / 1000,
                "exp", LocalDateTime.now().plusHours(4).toEpochSecond(ZoneOffset.UTC))));
        String unsigned = header + "." + payload;
        return unsigned + "." + hmacSha256(unsigned, JWT_SECRET);
    }

    private static String hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError("Could not sign JWT", e);
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
