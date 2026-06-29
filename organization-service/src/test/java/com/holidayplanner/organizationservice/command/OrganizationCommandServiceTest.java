package com.holidayplanner.organizationservice.command;

import com.holidayplanner.organizationservice.exception.NotFoundException;
import com.holidayplanner.organizationservice.kafka.OrganizationDeletionCompletionService;
import com.holidayplanner.organizationservice.kafka.OrganizationEventProducer;
import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.organizationservice.repository.SponsorRepository;
import com.holidayplanner.organizationservice.repository.TeamMemberRepository;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import com.holidayplanner.shared.model.TeamMemberRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the organization write side and the delete-organization saga's command steps
 * (start / rollback), with all collaborators mocked.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationCommandServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private SponsorRepository sponsorRepository;
    @Mock private OrganizationEventProducer eventProducer;
    @Mock private OrganizationDeletionCompletionService completionService;

    @InjectMocks private OrganizationCommandService service;

    private Organization org(OrganizationStatus status) {
        Organization o = new Organization();
        o.setId(UUID.randomUUID());
        o.setName("Demo Org");
        o.setBankAccount("AT123");
        o.setStatus(status);
        return o;
    }

    // --- create ---

    @Test
    void createOrganization_persistsAndPublishesCreatedEvent() {
        when(organizationRepository.existsByName("Demo Org")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> {
            Organization o = inv.getArgument(0);
            if (o.getId() == null) o.setId(UUID.randomUUID());
            return o;
        });

        Organization saved = service.createOrganization("Demo Org", "AT123", null);

        assertThat(saved.getName()).isEqualTo("Demo Org");
        verify(eventProducer).publishOrganizationCreated(any());
    }

    @Test
    void createOrganization_duplicateName_throwsIllegalState() {
        when(organizationRepository.existsByName("Demo Org")).thenReturn(true);

        assertThatThrownBy(() -> service.createOrganization("Demo Org", "AT123", null))
                .isInstanceOf(IllegalStateException.class);
        verify(organizationRepository, never()).save(any());
        verify(eventProducer, never()).publishOrganizationCreated(any());
    }

    @Test
    void createOrganization_blankName_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createOrganization("  ", "AT123", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(organizationRepository, never()).existsByName(any());
    }

    // --- update / members / sponsors ---

    @Test
    void updateOrganization_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(organizationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateOrganization(id, "AT999", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addTeamMember_orgNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(organizationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addTeamMember(
                id, UUID.randomUUID(), "Ann", "Smith", "ann@x.test", TeamMemberRole.TEAM_MEMBER))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addSponsor_negativeAmount_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.addSponsor(
                UUID.randomUUID(), "Acme", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeTeamMember_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(teamMemberRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.removeTeamMember(id))
                .isInstanceOf(NotFoundException.class);
        verify(teamMemberRepository, never()).deleteById(any());
    }

    @Test
    void removeSponsor_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(sponsorRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.removeSponsor(id))
                .isInstanceOf(NotFoundException.class);
        verify(sponsorRepository, never()).deleteById(any());
    }

    // --- delete saga: start ---

    @Test
    void deleteOrganization_whenActive_marksDeletingPublishesStartedAndArmsFallback() {
        Organization o = org(OrganizationStatus.ACTIVE);
        when(organizationRepository.findById(o.getId())).thenReturn(Optional.of(o));

        service.deleteOrganization(o.getId());

        assertThat(o.getStatus()).isEqualTo(OrganizationStatus.DELETING);
        verify(organizationRepository).save(o);
        verify(eventProducer).publishOrganizationDeletionStarted(any());
        verify(completionService).scheduleFallback(o.getId());
    }

    @Test
    void deleteOrganization_whenNotActive_throwsIllegalState() {
        Organization o = org(OrganizationStatus.DELETING);
        when(organizationRepository.findById(o.getId())).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.deleteOrganization(o.getId()))
                .isInstanceOf(IllegalStateException.class);
        verify(eventProducer, never()).publishOrganizationDeletionStarted(any());
        verify(completionService, never()).scheduleFallback(any());
    }

    @Test
    void deleteOrganization_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(organizationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteOrganization(id))
                .isInstanceOf(NotFoundException.class);
    }

    // --- delete saga: rollback ---

    @Test
    void rollbackDeletion_whenDeletingAndBeforePivot_restoresActiveAndPublishesRolledBack() {
        Organization o = org(OrganizationStatus.DELETING);
        when(organizationRepository.findById(o.getId())).thenReturn(Optional.of(o));
        when(completionService.isPivotCrossed(o.getId())).thenReturn(false);

        service.rollbackDeletion(o.getId());

        assertThat(o.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        verify(completionService).cancelPendingFinalization(o.getId());
        verify(organizationRepository).save(o);
        verify(eventProducer).publishOrganizationDeletionRolledBack(any());
    }

    @Test
    void rollbackDeletion_whenNotDeleting_throwsIllegalState() {
        Organization o = org(OrganizationStatus.ACTIVE);
        when(organizationRepository.findById(o.getId())).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.rollbackDeletion(o.getId()))
                .isInstanceOf(IllegalStateException.class);
        verify(eventProducer, never()).publishOrganizationDeletionRolledBack(any());
    }

    @Test
    void rollbackDeletion_whenPivotCrossed_throwsIllegalState() {
        Organization o = org(OrganizationStatus.DELETING);
        when(organizationRepository.findById(o.getId())).thenReturn(Optional.of(o));
        when(completionService.isPivotCrossed(o.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.rollbackDeletion(o.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(o.getStatus()).isEqualTo(OrganizationStatus.DELETING);
        verify(eventProducer, never()).publishOrganizationDeletionRolledBack(any());
        verify(completionService, never()).cancelPendingFinalization(any());
    }
}
