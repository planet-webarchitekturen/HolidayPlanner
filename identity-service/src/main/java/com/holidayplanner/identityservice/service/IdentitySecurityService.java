package com.holidayplanner.identityservice.service;

import com.holidayplanner.shared.security.JwtClaims;
import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("identitySecurity")
@RequiredArgsConstructor
public class IdentitySecurityService {

    private final FamilyMemberRepository familyMemberRepository;

    public boolean isSelf(UUID userId, Authentication auth) {
        return getClaims(auth).getUserId().equals(userId);
    }

    public boolean isFamilyMemberOwner(UUID memberId, Authentication auth) {
        return familyMemberRepository.findById(memberId)
                .map(member -> member.getUser().getId().equals(getClaims(auth).getUserId()))
                .orElse(false);
    }

    private JwtClaims getClaims(Authentication auth) {
        return (JwtClaims) auth.getPrincipal();
    }
}