package com.holidayplanner.identityservice.query;

import com.holidayplanner.identityservice.config.JwtTokenProvider;
import com.holidayplanner.identityservice.repository.CaregiverRepository;
import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import com.holidayplanner.identityservice.repository.UserRepository;
import com.holidayplanner.identityservice.service.RefreshTokenService;
import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityQueryService {

    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final CaregiverRepository caregiverRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    public List<FamilyMember> getFamilyMembers(UUID userId) {
        log.debug("Fetching family members for user {}", userId);
        return familyMemberRepository.findByUser_Id(userId);
    }

    public FamilyMember getFamilyMemberById(UUID memberId) {
        return familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("FamilyMember not found: " + memberId));
    }

    public Caregiver getCaregiverById(UUID caregiverId) {
        return caregiverRepository.findById(caregiverId)
                .orElseThrow(() -> new RuntimeException("Caregiver not found: " + caregiverId));
    }

    public List<Caregiver> getAllCaregivers() {
        return caregiverRepository.findAll();
    }

    public String getUserEmailByFamilyMemberId(UUID familyMemberId) {
        FamilyMember fm = familyMemberRepository.findById(familyMemberId)
                .orElseThrow(() -> new RuntimeException("Family member not found: " + familyMemberId));
        return fm.getUser().getEmail();
    }

    public String getFamilyMemberDisplayName(UUID familyMemberId) {
        FamilyMember fm = familyMemberRepository.findById(familyMemberId)
                .orElseThrow(() -> new RuntimeException("Family member not found: " + familyMemberId));
        return fm.getFirstName() + " " + fm.getLastName();
    }

    public String loginUser(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        List<String> roles = List.of(user.getRole().toString());
        return jwtTokenProvider.generateToken(user.getId(), user.getOrganizationId(), roles, user.getEmail());
    }

    public record LoginTokens(String accessToken, String refreshToken) { }

    public record LoginTokensWithUser(UUID userId, String accessToken, String refreshToken) { }

    public LoginTokens loginUserWithRefresh(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        List<String> roles = List.of(user.getRole().toString());
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getOrganizationId(), roles, user.getEmail());
        var refresh = refreshTokenService.createRefreshToken(user.getId());
        return new LoginTokens(accessToken, refresh.getToken());
    }

    public LoginTokensWithUser loginWithRefreshToken(String refreshToken) {
        var rt = refreshTokenService.findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));
        if (rt.isRevoked()) {
            throw new RuntimeException("Refresh token revoked");
        }
        if (rt.getExpiryDate().isBefore(Instant.now())) {
            throw new RuntimeException("Refresh token expired");
        }
        UUID userId = rt.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found for refresh token: " + userId));
        List<String> roles = List.of(user.getRole().toString());
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getOrganizationId(), roles, user.getEmail());
        var newRt = refreshTokenService.rotate(rt);
        return new LoginTokensWithUser(user.getId(), accessToken, newRt.getToken());
    }
}
