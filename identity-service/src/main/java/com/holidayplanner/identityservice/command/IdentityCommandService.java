package com.holidayplanner.identityservice.command;

import com.holidayplanner.identityservice.client.BookingServiceClient;
import com.holidayplanner.identityservice.exception.ActiveBookingVetoException;
import com.holidayplanner.identityservice.kafka.IdentityEventProducer;
import com.holidayplanner.identityservice.repository.CaregiverRepository;
import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import com.holidayplanner.identityservice.repository.UserRepository;
import com.holidayplanner.shared.kafka.payload.FamilyMemberAddedPayload;
import com.holidayplanner.shared.kafka.payload.FamilyMemberRemovedPayload;
import com.holidayplanner.shared.kafka.payload.UserDeletedPayload;
import com.holidayplanner.shared.kafka.payload.UserRegisteredPayload;
import com.holidayplanner.shared.kafka.payload.UserUpdatedPayload;
import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.shared.model.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

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

        UserRegisteredPayload payload = new UserRegisteredPayload(
                saved.getId(),
                saved.getEmail(),
                saved.getPhoneNumber(),
                saved.getOrganizationId(),
                saved.getRole());
        eventProducer.publishUserRegistered(payload);

        return saved;
    }

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

        UserUpdatedPayload payload = new UserUpdatedPayload(
                saved.getId(),
                saved.getEmail(),
                saved.getPhoneNumber());
        eventProducer.publishUserUpdated(payload);

        return saved;
    }

    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

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

        UserDeletedPayload payload = new UserDeletedPayload(userId, email, organizationId);
        eventProducer.publishUserDeleted(payload);
    }

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

        FamilyMemberAddedPayload payload = new FamilyMemberAddedPayload(
                userId,
                saved.getId(),
                firstName + " " + lastName);
        eventProducer.publishFamilyMemberAdded(payload);

        return saved;
    }

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

    public void removeFamilyMember(UUID memberId) {
        FamilyMember member = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("FamilyMember not found: " + memberId));

        long activeBookingCount = bookingServiceClient.getActiveBookingCount(memberId);
        if (activeBookingCount > 0) {
            throw new ActiveBookingVetoException("Cannot remove family member with active bookings. " +
                    "Cancel all bookings first (" + activeBookingCount + " active bookings found)");
        }

        UUID userId = member.getUser().getId();

        familyMemberRepository.deleteById(memberId);
        log.info("Family member {} removed", memberId);

        FamilyMemberRemovedPayload payload = new FamilyMemberRemovedPayload(
                userId,
                memberId);
        eventProducer.publishFamilyMemberRemoved(payload);
    }

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

    public void deleteCaregiver(UUID caregiverId) {
        Caregiver caregiver = caregiverRepository.findById(caregiverId)
                .orElseThrow(() -> new RuntimeException("Caregiver not found: " + caregiverId));
        caregiverRepository.delete(caregiver);
        log.info("Caregiver {} deleted", caregiverId);
    }
}
