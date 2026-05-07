package com.holidayplanner.identityservice.service;

import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.shared.kafka.payload.UserRegisteredPayload;
import com.holidayplanner.identityservice.config.JwtTokenProvider;
import com.holidayplanner.identityservice.kafka.IdentityEventProducer;
import com.holidayplanner.identityservice.repository.CaregiverRepository;
import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import com.holidayplanner.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityService {

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final CaregiverRepository caregiverRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityEventProducer identityEventProducer;
    private final JwtTokenProvider jwtTokenProvider;

    // --- User Operations ---

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

        UserRegisteredPayload payload = new UserRegisteredPayload(
                saved.getId(),
                saved.getEmail(),
                saved.getPhoneNumber(),
                saved.getOrganizationId(),
                saved.getRole()
        );
        identityEventProducer.publishUserRegistered(payload);

        return saved;
    }

    /**
     * Authenticate user and generate JWT token
     */
    public String loginUser(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        // Generate JWT token with user's roles
        List<String> roles = List.of(user.getRole().toString());
        return jwtTokenProvider.generateToken(user.getId(), user.getOrganizationId(), roles);
    }

    public User updatePhoneNumber(UUID userId, String phoneNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setPhoneNumber(phoneNumber);
        return userRepository.save(user);
    }

    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    // --- FamilyMember Operations ---

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

        return familyMemberRepository.save(member);
    }

    public FamilyMember updateFamilyMember(UUID memberId, String firstName, String lastName,
                                           LocalDate birthDate, String zip) {
        FamilyMember member = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("FamilyMember not found: " + memberId));

        member.setFirstName(firstName);
        member.setLastName(lastName);
        member.setBirthDate(birthDate);
        member.setZip(zip);

        return familyMemberRepository.save(member);
    }

    public void removeFamilyMember(UUID memberId) {
        // Note: BookingService must be checked first — cannot remove if active bookings exist
        familyMemberRepository.deleteById(memberId);
    }

    public List<FamilyMember> getFamilyMembers(UUID userId) {
        return familyMemberRepository.findByUser_Id(userId);
    }

    // --- Caregiver Operations ---

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

        return caregiverRepository.save(caregiver);
    }

    public Caregiver getCaregiverById(UUID caregiverId) {
        return caregiverRepository.findById(caregiverId)
                .orElseThrow(() -> new RuntimeException("Caregiver not found: " + caregiverId));
    }

    public List<Caregiver> getAllCaregivers() {
        return caregiverRepository.findAll();
    }
}
