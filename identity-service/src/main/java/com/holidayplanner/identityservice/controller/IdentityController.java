package com.holidayplanner.identityservice.controller;

import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.identityservice.command.IdentityCommandService;
import com.holidayplanner.identityservice.composition.IdentityCompositionService;
import com.holidayplanner.identityservice.dto.*;
import com.holidayplanner.identityservice.query.IdentityQueryService;
import com.holidayplanner.identityservice.service.IdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Identity Service Controller.
 * 
 * Routes requests to appropriate services following CQRS pattern:
 * - POST/PATCH/DELETE endpoints → IdentityCommandService (write operations)
 * - GET endpoints → IdentityQueryService (read operations)
 * - Composition GET endpoints → IdentityCompositionService (enriched reads)
 */
@RestController
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityCommandService commandService;
    private final IdentityQueryService queryService;
    private final IdentityCompositionService compositionService;
    private final IdentityService identityService;

    // --- Health Check ---
    @GetMapping("/api/identity/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("IdentityService is running!");
    }

    // --- Authentication Endpoints ---

    @PostMapping({"/api/auth/register", "/api/identity/users/register"})
    public ResponseEntity<UserResponse> register(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam("organizationId") UUID organizationId) {
        User user = commandService.registerUser(email, password, phoneNumber, organizationId);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping({"/api/auth/login", "/api/identity/auth/login"})
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest loginRequest) {
        String token = identityService.loginUser(loginRequest.getEmail(), loginRequest.getPassword());
        User user = queryService.getUserByEmail(loginRequest.getEmail());
        return ResponseEntity.ok(new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getOrganizationId(),
                user.getRole(),
                token
        ));
    }

    // --- User Endpoints ---

    @GetMapping("/api/identity/users/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ORGANIZATION_TEAM_MEMBER', 'ADMIN', 'EVENT_OWNER', 'ACCOUNTANT')")
    public ResponseEntity<UserResponse> getUser(@PathVariable("userId") UUID userId) {
        User user = queryService.getUserById(userId);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping("/api/identity/users/{userId}/profile")
    public ResponseEntity<UserProfileEnrichedResponse> getUserProfile(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(compositionService.getUserProfileEnriched(userId));
    }

    @PatchMapping("/api/identity/users/{userId}/phone")
    public ResponseEntity<UserResponse> updatePhone(
            @PathVariable("userId") UUID userId,
            @RequestParam("phoneNumber") String phoneNumber) {
        User user = commandService.updatePhoneNumber(userId, phoneNumber);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    // --- FamilyMember Endpoints ---

    @GetMapping("/api/identity/users/{userId}/family-members")
    public ResponseEntity<List<FamilyMember>> getFamilyMembers(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(queryService.getFamilyMembers(userId));
    }

    @PostMapping("/api/identity/users/{userId}/family-members")
    public ResponseEntity<FamilyMember> addFamilyMember(
            @PathVariable("userId") UUID userId,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("birthDate") LocalDate birthDate,
            @RequestParam("zip") String zip) {
        return ResponseEntity.ok(commandService.addFamilyMember(userId, firstName, lastName, birthDate, zip));
    }

    @PutMapping("/api/identity/family-members/{memberId}")
    public ResponseEntity<FamilyMember> updateFamilyMember(
            @PathVariable("memberId") UUID memberId,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("birthDate") LocalDate birthDate,
            @RequestParam("zip") String zip) {
        return ResponseEntity.ok(commandService.updateFamilyMember(memberId, firstName, lastName, birthDate, zip));
    }

    @DeleteMapping("/api/identity/family-members/{memberId}")
    public ResponseEntity<Void> removeFamilyMember(@PathVariable("memberId") UUID memberId) {
        commandService.removeFamilyMember(memberId);
        return ResponseEntity.noContent().build();
    }

    // --- Caregiver Endpoints ---

    @PostMapping("/api/identity/caregivers")
    public ResponseEntity<Caregiver> createCaregiver(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam("phoneNumber") String phoneNumber) {
        return ResponseEntity.ok(commandService.createCaregiver(firstName, lastName, email, phoneNumber));
    }

    @GetMapping("/api/identity/caregivers")
    public ResponseEntity<List<Caregiver>> getAllCaregivers() {
        return ResponseEntity.ok(queryService.getAllCaregivers());
    }

    @GetMapping("/api/identity/caregivers/{caregiverId}")
    public ResponseEntity<Caregiver> getCaregiver(@PathVariable("caregiverId") UUID caregiverId) {
        return ResponseEntity.ok(queryService.getCaregiverById(caregiverId));
    }
}
