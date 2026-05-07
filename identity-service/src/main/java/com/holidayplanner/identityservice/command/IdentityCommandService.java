package com.holidayplanner.identityservice.command;

import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.identityservice.repository.CaregiverRepository;
import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import com.holidayplanner.identityservice.repository.UserRepository;
import com.holidayplanner.identityservice.event.DomainEventPublisher;
import com.holidayplanner.identityservice.event.UserRegisteredEvent;
import com.holidayplanner.identityservice.event.UserPhoneUpdatedEvent;
import com.holidayplanner.identityservice.event.FamilyMemberAddedEvent;
import com.holidayplanner.identityservice.event.FamilyMemberRemovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * Command Service for Identity Service.
 * 
 * Handles all write operations (commands):
 * - User registration and updates
 * - Family member creation, updates, and deletion
 * - Caregiver management
 * 
 * Commands modify state and should be transactional.
 * They are separated from queries (IdentityQueryService) following CQRS pattern,
 * allowing each to be optimized independently.
 * 
 * In the future, this service will publish Kafka events (e.g., identity.user.registered)
 * to trigger side effects in other services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityCommandService {

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final CaregiverRepository caregiverRepository;
    private final PasswordEncoder passwordEncoder;
    private final DomainEventPublisher eventPublisher;

    /**
     * Register a new user in the system.
     * 
     * @param email user's email (must be unique)
     * @param password user's password (will be hashed)
     * @param phoneNumber user's phone number
     * @param organizationId the organization this user belongs to
     * @return the created User
     * @throws RuntimeException if email already exists
     */
    public User registerUser(String email, String password,
                             String phoneNumber, UUID organizationId) {
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("User already exists with email: " + email);
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPhoneNumber(phoneNumber);
        user.setOrganizationId(organizationId);

        User saved = userRepository.save(user);
        log.info("User registered: {} for organization {}", email, organizationId);
        
        // Publish domain event
        UserRegisteredEvent event = new UserRegisteredEvent(
                saved.getId(),
                saved.getEmail(),
                saved.getOrganizationId(),
                Instant.now()
        );
        eventPublisher.publishUserRegistered(event);
        
        return saved;
    }

    /**
     * Update a user's phone number.
     * 
     * @param userId the user's ID
     * @param phoneNumber the new phone number
     * @return the updated User
     * @throws RuntimeException if user not found
     */
    public User updatePhoneNumber(UUID userId, String phoneNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setPhoneNumber(phoneNumber);
        User saved = userRepository.save(user);
        log.debug("User {} phone number updated", userId);
        
        // Publish domain event
        UserPhoneUpdatedEvent event = new UserPhoneUpdatedEvent(
                saved.getId(),
                saved.getPhoneNumber(),
                Instant.now()
        );
        eventPublisher.publishUserPhoneUpdated(event);
        
        return saved;
    }

    /**
     * Add a family member to a user's profile.
     * 
     * @param userId the parent/user's ID
     * @param firstName family member's first name
     * @param lastName family member's last name
     * @param birthDate family member's birth date
     * @param zip family member's zip code
     * @return the created FamilyMember
     * @throws RuntimeException if user not found
     */
    public FamilyMember addFamilyMember(UUID userId, String firstName, String lastName,
                                        LocalDate birthDate, String zip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        FamilyMember member = new FamilyMember();
        member.setUser(user);
        member.setFirstName(firstName);
        member.setLastName(lastName);
        member.setBirthDate(birthDate);
        member.setZip(zip);

        FamilyMember saved = familyMemberRepository.save(member);
        log.info("Family member {} {} added to user {}", firstName, lastName, userId);
        
        // Publish domain event
        FamilyMemberAddedEvent event = new FamilyMemberAddedEvent(
                saved.getId(),
                userId,
                firstName,
                lastName,
                birthDate,
                zip,
                Instant.now()
        );
        eventPublisher.publishFamilyMemberAdded(event);
        
        return saved;
    }

    /**
     * Update a family member's information.
     * 
     * @param memberId the family member's ID
     * @param firstName new first name
     * @param lastName new last name
     * @param birthDate new birth date
     * @param zip new zip code
     * @return the updated FamilyMember
     * @throws RuntimeException if family member not found
     */
    public FamilyMember updateFamilyMember(UUID memberId, String firstName, String lastName,
                                           LocalDate birthDate, String zip) {
        FamilyMember member = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("FamilyMember not found: " + memberId));

        member.setFirstName(firstName);
        member.setLastName(lastName);
        member.setBirthDate(birthDate);
        member.setZip(zip);

        FamilyMember saved = familyMemberRepository.save(member);
        log.debug("Family member {} updated", memberId);
        return saved;
    }

    /**
     * Remove a family member from the system.
     * 
     * IMPORTANT: In production, this should check with booking-service to ensure
     * the family member has no active bookings before deletion. For now, deletion is allowed.
     * 
     * @param memberId the family member's ID
     * @throws RuntimeException if family member not found
     */
    public void removeFamilyMember(UUID memberId) {
        FamilyMember member = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("FamilyMember not found: " + memberId));
        
        // TODO: Check with booking-service: hasActiveBookings(memberId)
        // If true, reject deletion to maintain data consistency
        
        String firstName = member.getFirstName();
        String lastName = member.getLastName();
        UUID userId = member.getUser().getId();
        
        familyMemberRepository.deleteById(memberId);
        log.info("Family member {} removed", memberId);
        
        // Publish domain event
        FamilyMemberRemovedEvent event = new FamilyMemberRemovedEvent(
                memberId,
                userId,
                firstName,
                lastName,
                Instant.now()
        );
        eventPublisher.publishFamilyMemberRemoved(event);
    }

    /**
     * Create a new caregiver in the system.
     * 
     * @param firstName caregiver's first name
     * @param lastName caregiver's last name
     * @param email caregiver's email (must be unique)
     * @param phoneNumber caregiver's phone number
     * @return the created Caregiver
     * @throws RuntimeException if email already exists
     */
    public Caregiver createCaregiver(String firstName, String lastName,
                                     String email, String phoneNumber) {
        if (caregiverRepository.existsByEmail(email)) {
            throw new RuntimeException("Caregiver already exists with email: " + email);
        }

        Caregiver caregiver = new Caregiver();
        caregiver.setFirstName(firstName);
        caregiver.setLastName(lastName);
        caregiver.setEmail(email);
        caregiver.setPhoneNumber(phoneNumber);

        Caregiver saved = caregiverRepository.save(caregiver);
        log.info("Caregiver created: {} {}", firstName, lastName);
        return saved;
    }
}
