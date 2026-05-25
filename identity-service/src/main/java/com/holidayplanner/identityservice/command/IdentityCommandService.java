package com.holidayplanner.identityservice.command;

import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.shared.kafka.payload.FamilyMemberAddedPayload;
import com.holidayplanner.shared.kafka.payload.FamilyMemberRemovedPayload;
import com.holidayplanner.shared.kafka.payload.UserPhoneUpdatedPayload;
import com.holidayplanner.shared.kafka.payload.UserOrganizationUpdatedPayload;
import com.holidayplanner.shared.kafka.payload.UserRegisteredPayload;
import com.holidayplanner.identityservice.client.BookingServiceClient;
import com.holidayplanner.identityservice.kafka.IdentityEventProducer;
import com.holidayplanner.identityservice.repository.CaregiverRepository;
import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import com.holidayplanner.identityservice.repository.UserRepository;
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
 * Handles all WRITE operations (commands):
 * - User registration and updates
 * - Family member creation, updates, and deletion
 * - Caregiver management
 */
@Slf4j
@Service
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

    //CHECK: Do we only need this update or do other parameters also need to be updateable? not in system operations
    //CHECK: This is not in the system operations at all
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
        
        // Publish Kafka event
        //can't return the result directly because we dont want to hand of the password hash back to the user
        UserPhoneUpdatedPayload payload = new UserPhoneUpdatedPayload(
                saved.getId(),
                saved.getPhoneNumber()
        );
        eventProducer.publishUserPhoneUpdated(payload);
        
        return saved;
    }
    //CHECK: Delete user? not in system operations

    /**
     * Update a user's organization. Can be set to null when the organization is deleted.
     * 
     * @param userId the user's ID
     * @param organizationId the new organization ID (can be null)
     * @return the updated User
     * @throws RuntimeException if user not found
     */
    public User updateOrganization(UUID userId, UUID organizationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setOrganizationId(organizationId);
        User saved = userRepository.save(user);
        log.debug("User {} organization updated to {}", userId, organizationId);
        
        // Publish Kafka event
        UserOrganizationUpdatedPayload payload = new UserOrganizationUpdatedPayload(
                saved.getId(),
                saved.getOrganizationId()
        );
        eventProducer.publishUserOrganizationUpdated(payload);
        
        return saved;
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
            throw new RuntimeException("Cannot remove family member with active bookings. " +
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

    //CHECK: update/remove cargiver? Not in system operations
}
