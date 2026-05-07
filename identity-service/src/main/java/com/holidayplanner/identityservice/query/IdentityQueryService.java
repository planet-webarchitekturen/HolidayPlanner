package com.holidayplanner.identityservice.query;

import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.identityservice.repository.CaregiverRepository;
import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import com.holidayplanner.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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

    /**
     * Fetch all caregivers in the system.
     * 
     * @return list of all Caregivers
     */
    public List<Caregiver> getAllCaregivers() {
        return caregiverRepository.findAll();
    }
}
