package com.holidayplanner.organizationservice.controller;

import com.holidayplanner.organizationservice.command.OrganizationCommandService;
import com.holidayplanner.organizationservice.dto.EnrichedTeamMemberResponse;
import com.holidayplanner.organizationservice.dto.OrganizationOverviewResponse;
import com.holidayplanner.organizationservice.dto.OrganizationResponse;
import com.holidayplanner.organizationservice.dto.SponsorResponse;
import com.holidayplanner.organizationservice.dto.TeamMemberResponse;
import com.holidayplanner.organizationservice.query.OrganizationQueryService;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.Sponsor;
import com.holidayplanner.shared.model.TeamMember;
import com.holidayplanner.shared.model.TeamMemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationCommandService organizationCommandService;
    private final OrganizationQueryService organizationQueryService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OrganizationService is running!");
    }

    // --- Organization Endpoints ---

    @PostMapping
    public ResponseEntity<Organization> createOrganization(
            @RequestParam("name") String name,
            @RequestParam("bankAccount") String bankAccount,
            @RequestParam(value = "bookingStartTime", required = false) LocalDateTime bookingStartTime) {
        return ResponseEntity.ok(organizationCommandService.createOrganization(name, bankAccount, bookingStartTime));
    }

    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> getAllOrganizations() {
        return ResponseEntity.ok(organizationQueryService.getAllOrganizations());
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<OrganizationResponse> getOrganization(@PathVariable("organizationId") UUID organizationId) {
        return ResponseEntity.ok(organizationQueryService.getOrganization(organizationId));
    }

    @GetMapping("/{organizationId}/overview")
    public ResponseEntity<OrganizationOverviewResponse> getOrganizationOverview(
            @PathVariable("organizationId") UUID organizationId) {
        return ResponseEntity.ok(organizationQueryService.getOrganizationOverview(organizationId));
    }

    @PutMapping("/{organizationId}")
    public ResponseEntity<Organization> updateOrganization(
            @PathVariable("organizationId") UUID organizationId,
            @RequestParam("bankAccount") String bankAccount,
            @RequestParam(value = "bookingStartTime", required = false) LocalDateTime bookingStartTime) {
        return ResponseEntity.ok(organizationCommandService.updateOrganization(organizationId, bankAccount, bookingStartTime));
    }

    // --- TeamMember Endpoints ---

    @GetMapping("/{organizationId}/team-members")
    public ResponseEntity<List<TeamMemberResponse>> getTeamMembers(@PathVariable("organizationId") UUID organizationId) {
        return ResponseEntity.ok(organizationQueryService.getTeamMembers(organizationId));
    }

    @GetMapping("/{organizationId}/team-members/enriched")
    public ResponseEntity<List<EnrichedTeamMemberResponse>> getEnrichedTeamMembers(
            @PathVariable("organizationId") UUID organizationId) {
        return ResponseEntity.ok(organizationQueryService.getOrganizationOverview(organizationId).getTeamMembers());
    }

    @PostMapping("/{organizationId}/team-members")
    public ResponseEntity<TeamMember> addTeamMember(
            @PathVariable("organizationId") UUID organizationId,
            @RequestParam("userId") UUID userId,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam(value = "role", defaultValue = "TEAM_MEMBER") TeamMemberRole role) {
        return ResponseEntity.ok(organizationCommandService.addTeamMember(
                organizationId, userId, firstName, lastName, email, role));
    }

    @DeleteMapping("/team-members/{teamMemberId}")
    public ResponseEntity<Void> removeTeamMember(@PathVariable("teamMemberId") UUID teamMemberId) {
        organizationCommandService.removeTeamMember(teamMemberId);
        return ResponseEntity.noContent().build();
    }

    // --- Sponsor Endpoints ---

    @GetMapping("/{organizationId}/sponsors")
    public ResponseEntity<List<SponsorResponse>> getSponsors(@PathVariable("organizationId") UUID organizationId) {
        return ResponseEntity.ok(organizationQueryService.getSponsors(organizationId));
    }

    @PostMapping("/{organizationId}/sponsors")
    public ResponseEntity<Sponsor> addSponsor(
            @PathVariable("organizationId") UUID organizationId,
            @RequestParam("name") String name,
            @RequestParam(value = "amount", required = false) BigDecimal amount) {
        return ResponseEntity.ok(organizationCommandService.addSponsor(organizationId, name, amount));
    }

    @DeleteMapping("/sponsors/{sponsorId}")
    public ResponseEntity<Void> removeSponsor(@PathVariable("sponsorId") UUID sponsorId) {
        organizationCommandService.removeSponsor(sponsorId);
        return ResponseEntity.noContent().build();
    }
}
