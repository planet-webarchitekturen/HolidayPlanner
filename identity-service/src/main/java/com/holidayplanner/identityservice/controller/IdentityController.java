package com.holidayplanner.identityservice.controller;

import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.identityservice.command.IdentityCommandService;
import com.holidayplanner.identityservice.dto.*;
import com.holidayplanner.identityservice.query.IdentityQueryService;
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
 */
@RestController
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityCommandService commandService;
    private final IdentityQueryService queryService;

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

    @PostMapping({"​/api/auth/login", "/api/identity/auth/login"})
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest loginRequest) {
        String token = queryService.loginUser(loginRequest.getEmail(), loginRequest.getPassword());
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

    @GetMapping("/api/identity/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = queryService.getAllUsers().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/api/identity/users/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ORGANIZATION_TEAM_MEMBER', 'ADMIN', 'EVENT_OWNER', 'ACCOUNTANT')")
    public ResponseEntity<UserResponse> getUser(@PathVariable("userId") UUID userId) {
        User user = queryService.getUserById(userId);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PatchMapping("/api/identity/users/{userId}")
    @PreAuthorize("@identitySecurity.canUpdateUser(#userId, #request, authentication)")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable("userId") UUID userId,
            @RequestBody UpdateUserRequest request) {
        User user = commandService.updateUser(userId, request.getEmail(), request.getPhoneNumber(),
                request.getPassword(), request.getRole(), request.getOrganizationId());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @DeleteMapping("/api/identity/users/{userId}")
    @PreAuthorize("@identitySecurity.isSelf(#userId, authentication) or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable("userId") UUID userId) {
        commandService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // --- FamilyMember Endpoints ---

    @GetMapping("/api/identity/users/{userId}/family-members")
    @PreAuthorize("@identitySecurity.isSelf(#userId, authentication)")
    public ResponseEntity<List<FamilyMember>> getFamilyMembers(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(queryService.getFamilyMembers(userId));
    }

    @PostMapping("/api/identity/users/{userId}/family-members")
    @PreAuthorize("@identitySecurity.isSelf(#userId, authentication)")
    public ResponseEntity<FamilyMember> addFamilyMember(
            @PathVariable("userId") UUID userId,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("birthDate") LocalDate birthDate,
            @RequestParam("zip") String zip) {
        return ResponseEntity.ok(commandService.addFamilyMember(userId, firstName, lastName, birthDate, zip));
    }

    @GetMapping("/api/identity/family-members/{memberId}")
    @PreAuthorize("@identitySecurity.isFamilyMemberOwner(#memberId, authentication) or hasAnyRole('ORGANIZATION_TEAM_MEMBER','ADMIN','EVENT_OWNER')")
    public ResponseEntity<FamilyMember> getFamilyMember(@PathVariable("memberId") UUID memberId) {
        return ResponseEntity.ok(queryService.getFamilyMemberById(memberId));
    }

    @PutMapping("/api/identity/family-members/{memberId}")
    @PreAuthorize("@identitySecurity.isFamilyMemberOwner(#memberId, authentication)")
    public ResponseEntity<FamilyMember> updateFamilyMember(
            @PathVariable("memberId") UUID memberId,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("birthDate") LocalDate birthDate,
            @RequestParam("zip") String zip) {
        return ResponseEntity.ok(commandService.updateFamilyMember(memberId, firstName, lastName, birthDate, zip));
    }

    @GetMapping("/api/identity/family-members/{memberId}/owner-email")
    @PreAuthorize("hasAnyRole('ORGANIZATION_TEAM_MEMBER','ADMIN','EVENT_OWNER')")
    public ResponseEntity<java.util.Map<String, String>> getFamilyMemberOwnerEmail(
            @PathVariable("memberId") UUID memberId) {
        String email = queryService.getUserEmailByFamilyMemberId(memberId);
        return ResponseEntity.ok(java.util.Map.of("email", email));
    }

    @GetMapping("/api/identity/family-members/{memberId}/display-name")
    @PreAuthorize("hasAnyRole('ORGANIZATION_TEAM_MEMBER','ADMIN','EVENT_OWNER')")
    public ResponseEntity<java.util.Map<String, String>> getFamilyMemberDisplayName(
            @PathVariable("memberId") UUID memberId) {
        String name = queryService.getFamilyMemberDisplayName(memberId);
        return ResponseEntity.ok(java.util.Map.of("name", name));
    }

    @DeleteMapping("/api/identity/family-members/{memberId}")
    @PreAuthorize("@identitySecurity.isFamilyMemberOwner(#memberId, authentication)")
    public ResponseEntity<Void> removeFamilyMember(@PathVariable("memberId") UUID memberId) {
        commandService.removeFamilyMember(memberId);
        return ResponseEntity.noContent().build();
    }

    // --- Caregiver Endpoints ---

    //CHECK: Can only Admins and event owners create caregivers? or anyone?
    @PostMapping("/api/identity/caregivers")
    @PreAuthorize("hasAnyRole('EVENT_OWNER','ADMIN')")
    public ResponseEntity<Caregiver> createCaregiver(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam("phoneNumber") String phoneNumber) {
        return ResponseEntity.ok(commandService.createCaregiver(firstName, lastName, email, phoneNumber));
    }

    @GetMapping("/api/identity/caregivers")
    @PreAuthorize("hasAnyRole('EVENT_OWNER','ADMIN','ORGANIZATION_TEAM_MEMBER')")
    public ResponseEntity<List<Caregiver>> getAllCaregivers() {
        return ResponseEntity.ok(queryService.getAllCaregivers());
    }

    @GetMapping("/api/identity/caregivers/{caregiverId}")
    @PreAuthorize("hasAnyRole('EVENT_OWNER','ADMIN','ORGANIZATION_TEAM_MEMBER')")
    public ResponseEntity<Caregiver> getCaregiver(@PathVariable("caregiverId") UUID caregiverId) {
        return ResponseEntity.ok(queryService.getCaregiverById(caregiverId));
    }

    @PutMapping("/api/identity/caregivers/{caregiverId}")
    @PreAuthorize("hasAnyRole('EVENT_OWNER','ADMIN')")
    public ResponseEntity<Caregiver> updateCaregiver(
            @PathVariable("caregiverId") UUID caregiverId,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam("phoneNumber") String phoneNumber) {
        return ResponseEntity.ok(commandService.updateCaregiver(caregiverId, firstName, lastName, email, phoneNumber));
    }

    @DeleteMapping("/api/identity/caregivers/{caregiverId}")
    @PreAuthorize("hasAnyRole('EVENT_OWNER','ADMIN')")
    public ResponseEntity<Void> deleteCaregiver(@PathVariable("caregiverId") UUID caregiverId) {
        commandService.deleteCaregiver(caregiverId);
        return ResponseEntity.noContent().build();
    }
}
