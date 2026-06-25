package com.holidayplanner.identityservice.command;

import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.shared.model.UserRole;
import com.holidayplanner.shared.kafka.payload.FamilyMemberAddedPayload;
import com.holidayplanner.shared.kafka.payload.FamilyMemberRemovedPayload;
import com.holidayplanner.shared.kafka.payload.UserDeletedPayload;
import com.holidayplanner.shared.kafka.payload.UserUpdatedPayload;
import com.holidayplanner.shared.kafka.payload.UserRegisteredPayload;
import com.holidayplanner.identityservice.client.BookingServiceClient;
import com.holidayplanner.identityservice.exception.ActiveBookingVetoException;
import com.holidayplanner.identityservice.kafka.IdentityEventProducer;
import com.holidayplanner.identityservice.repository.CaregiverRepository;
import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import com.holidayplanner.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command Service for Identity Service.
 *
 * Handles all WRITE operations (commands):
 * - User registration and updates
 * - Family member creation, updates, and deletion
 * - Caregiver management
 *
 * Every command runs in a transaction: the aggregate change and the outbox row
 * carrying its domain event commit together (transactional outbox pattern).
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class IdentityCommandService {

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final CaregiverRepository caregiverRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityEventProducer eventProducer;
    private final BookingServiceClient bookingServiceClient;




    // --- User Operations ---
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
        
        // Publish Kafka event
        UserRegisteredPayload payload = new UserRegisteredPayload(
                saved.getId(),
                saved.getEmail(),
                saved.getPhoneNumber(),
                saved.getOrganizationId(),
                saved.getRole()
        );
        eventProducer.publishUserRegistered(payload);
        
        return saved;
    }

    /**
     * Update a user's profile.
     *
     * Partial update: any argument left null is left unchanged. Email is re-checked
     * for uniqueness, the password is re-hashed, and {@code role}/{@code organizationId}
     * are administrative fields (authorization is enforced in the controller layer).
     *
     * @param userId the user's ID
     * @param email new email, or null to keep the current one
     * @param phoneNumber new phone number, or null to keep the current one
     * @param password new plaintext password, or null to keep the current one
     * @param role new role, or null to keep the current one
     * @param organizationId new organization, or null to keep the current one
     * @return the updated User
     * @throws RuntimeException if user not found, or if the new email is already in use
     */
    public User updateUser(UUID userId, String email, String phoneNumber,
                           String password, UserRole role, UUID organizationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new RuntimeException("User already exists with email: " + email);
            }
            user.setEmail(email);
        }
        if (phoneNumber != null) {
            user.setPhoneNumber(phoneNumber);
        }
        if (password != null) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        if (role != null) {
            user.setRole(role);
        }
        if (organizationId != null) {
            user.setOrganizationId(organizationId);
        }

        User saved = userRepository.save(user);
        log.info("User {} updated", userId);

        // Publish Kafka event.
        // We don't return the entity directly to consumers because we never want to
        // expose the password hash; the event carries only the public profile fields.
        UserUpdatedPayload payload = new UserUpdatedPayload(
                saved.getId(),
                saved.getEmail(),
                saved.getPhoneNumber()
        );
        eventProducer.publishUserUpdated(payload);

        return saved;
    }

    /**
     * Delete a user and their family members.
     *
     * Pre-condition: none of the user's family members have active bookings
     * (checked via booking-service, mirroring {@link #removeFamilyMember(UUID)}).
     * Deleting the user cascades to their family members (orphanRemoval).
     *
     * @param userId the user's ID
     * @throws RuntimeException if user not found
     * @throws RuntimeException if any family member has active bookings
     */
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Veto check: ensure no family member has active bookings before cascading the delete
        for (FamilyMember member : familyMemberRepository.findByUser_Id(userId)) {
            long activeBookingCount = bookingServiceClient.getActiveBookingCount(member.getId());
            if (activeBookingCount > 0) {
                throw new ActiveBookingVetoException("Cannot delete user with family members that have active bookings. " +
                        "Cancel all bookings first (" + activeBookingCount + " active bookings found for member " +
                        member.getId() + ")");
            }
        }

        String email = user.getEmail();
        UUID organizationId = user.getOrganizationId();

        userRepository.delete(user);
        log.info("User {} deleted", userId);

        // Publish Kafka event
        UserDeletedPayload payload = new UserDeletedPayload(userId, email, organizationId);
        eventProducer.publishUserDeleted(payload);
    }

    // --- FamilyMember Operations ---

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
        
        // Publish Kafka event
        FamilyMemberAddedPayload payload = new FamilyMemberAddedPayload(
                userId,
                saved.getId(),
                firstName + " " + lastName
        );
        eventProducer.publishFamilyMemberAdded(payload);
        
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
     * Pre-condition: Family member has no active bookings (checked via booking-service).
     * If active bookings exist, deletion is rejected to maintain data consistency.
     * 
     * @param memberId the family member's ID
     * @throws RuntimeException if family member not found
     * @throws RuntimeException if family member has active bookings
     */
    public void removeFamilyMember(UUID memberId) {
        FamilyMember member = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("FamilyMember not found: " + memberId));
        
        // Veto check: ensure no active bookings exist for this family member
        long activeBookingCount = bookingServiceClient.getActiveBookingCount(memberId);
        if (activeBookingCount > 0) {
            throw new ActiveBookingVetoException("Cannot remove family member with active bookings. " +
                    "Cancel all bookings first (" + activeBookingCount + " active bookings found)");
        }
        
        String firstName = member.getFirstName();
        String lastName = member.getLastName();
        UUID userId = member.getUser().getId();
        
        familyMemberRepository.deleteById(memberId);
        log.info("Family member {} removed", memberId);
        
        // Publish Kafka event
        FamilyMemberRemovedPayload payload = new FamilyMemberRemovedPayload(
                userId,
                memberId
        );
        eventProducer.publishFamilyMemberRemoved(payload);
    }

    // --- Caregiver Operations ---

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

    /**
     * Update a caregiver's information.
     *
     * @param caregiverId the caregiver's ID
     * @param firstName new first name
     * @param lastName new last name
     * @param email new email (must remain unique)
     * @param phoneNumber new phone number
     * @return the updated Caregiver
     * @throws RuntimeException if caregiver not found, or if the new email is already in use
     */
    public Caregiver updateCaregiver(UUID caregiverId, String firstName, String lastName,
                                     String email, String phoneNumber) {
        Caregiver caregiver = caregiverRepository.findById(caregiverId)
                .orElseThrow(() -> new RuntimeException("Caregiver not found: " + caregiverId));

        if (!email.equals(caregiver.getEmail()) && caregiverRepository.existsByEmail(email)) {
            throw new RuntimeException("Caregiver already exists with email: " + email);
        }

        caregiver.setFirstName(firstName);
        caregiver.setLastName(lastName);
        caregiver.setEmail(email);
        caregiver.setPhoneNumber(phoneNumber);

        Caregiver saved = caregiverRepository.save(caregiver);
        log.info("Caregiver {} updated", caregiverId);
        return saved;
    }

    /**
     * Delete a caregiver from the system.
     *
     * @param caregiverId the caregiver's ID
     * @throws RuntimeException if caregiver not found
     */
    public void deleteCaregiver(UUID caregiverId) {
        Caregiver caregiver = caregiverRepository.findById(caregiverId)
                .orElseThrow(() -> new RuntimeException("Caregiver not found: " + caregiverId));
        caregiverRepository.delete(caregiver);
        log.info("Caregiver {} deleted", caregiverId);
    }
}
