package com.holidayplanner.identityservice.query;

import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.identityservice.config.JwtTokenProvider;
import com.holidayplanner.identityservice.repository.CaregiverRepository;
import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import com.holidayplanner.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
//CHECK: We have Authentication via Security Config but we need to add authorization
/**
 * Query Service for Identity Service.
 * 
 * Handles all read operations (queries):
 * - Fetch user profiles
 * - Fetch family members
 * - Fetch caregiver details
 * 
 * Queries are read-only and do not modify state.
 * They are separated from commands (IdentityCommandService) following CQRS pattern.
 * This allows queries to be optimized independently:
 * - Future: add caching, read replicas, or dedicated read models
 * - Future: denormalization for reporting queries
 * 
 * The separation also ensures that query code is not intertwined with business logic
 * (e.g., Kafka event publishing), making both easier to test and maintain.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityQueryService {

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final CaregiverRepository caregiverRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;


    /**
     * Fetch a user by ID.
     * 
     * @param userId the user's ID
     * @return the User
     * @throws RuntimeException if user not found
     */
    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    //CHECK: Not in System Operations BUT used in Login
    /**
     * Fetch a user by email.
     * 
     * @param email the user's email
     * @return the User
     * @throws RuntimeException if user not found
     */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    /**
     * Fetch all family members for a given user.
     * 
     * @param userId the parent/user's ID
     * @return list of FamilyMembers (empty if user has no family members)
     */
    public List<FamilyMember> getFamilyMembers(UUID userId) {
        log.debug("Fetching family members for user {}", userId);
        return familyMemberRepository.findByUser_Id(userId);
    }

    /**
     * Fetch a caregiver by ID.
     * 
     * @param caregiverId the caregiver's ID
     * @return the Caregiver
     * @throws RuntimeException if caregiver not found
     */
    public Caregiver getCaregiverById(UUID caregiverId) {
        return caregiverRepository.findById(caregiverId)
                .orElseThrow(() -> new RuntimeException("Caregiver not found: " + caregiverId));
    }

    //CHECK: Not in System Operations
    /**
     * Fetch all caregivers in the system.
     * 
     * @return list of all Caregivers
     */
    public List<Caregiver> getAllCaregivers() {
        return caregiverRepository.findAll();
    }

    //CHECK: Not in System Operations
    public String getUserEmailByFamilyMemberId(UUID familyMemberId) {
        FamilyMember fm = familyMemberRepository.findById(familyMemberId)
                .orElseThrow(() -> new RuntimeException("Family member not found: " + familyMemberId));
        return fm.getUser().getEmail();
    }

    //CHECK: Not in System Operations
    public String getFamilyMemberDisplayName(UUID familyMemberId) {
        FamilyMember fm = familyMemberRepository.findById(familyMemberId)
                .orElseThrow(() -> new RuntimeException("Family member not found: " + familyMemberId));
        return fm.getFirstName() + " " + fm.getLastName();
    }

    /**
     * Authenticate user and generate JWT token.
     * 
     * This is a read operation in CQRS (it doesn't modify state, only looks up the user),
     * though the JWT generation is a side effect. Kept here with QueryService for simplicity.
     * 
     * @param email the user's email
     * @param password the user's plaintext password
     * @return JWT token
     * @throws RuntimeException if user not found or password is invalid
     */
    public String loginUser(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        List<String> roles = List.of(user.getRole().toString());
        return jwtTokenProvider.generateToken(user.getId(), user.getOrganizationId(), roles, user.getEmail());
    }
}
