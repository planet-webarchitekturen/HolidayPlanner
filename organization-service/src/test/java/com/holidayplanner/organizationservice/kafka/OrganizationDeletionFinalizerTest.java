package com.holidayplanner.organizationservice.kafka;

import com.holidayplanner.organizationservice.repository.OrganizationRepository;
import com.holidayplanner.shared.model.Organization;
import com.holidayplanner.shared.model.OrganizationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the terminal saga step: it finalizes only when the org is still DELETING (idempotent
 * guard) and otherwise does nothing.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationDeletionFinalizerTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationEventProducer eventProducer;

    @InjectMocks private OrganizationDeletionFinalizer finalizer;

    @Test
    void finalizeIfStillDeleting_whenDeleting_setsDeletedAndPublishes() {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName("Demo Org");
        org.setStatus(OrganizationStatus.DELETING);
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));

        boolean finalized = finalizer.finalizeIfStillDeleting(org.getId());

        assertThat(finalized).isTrue();
        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.DELETED);
        verify(organizationRepository).save(org);
        verify(eventProducer).publishOrganizationDeleted(any());
    }

    @Test
    void finalizeIfStillDeleting_whenNotDeleting_doesNothing() {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setStatus(OrganizationStatus.ACTIVE);
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));

        boolean finalized = finalizer.finalizeIfStillDeleting(org.getId());

        assertThat(finalized).isFalse();
        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        verify(organizationRepository, never()).save(any());
        verify(eventProducer, never()).publishOrganizationDeleted(any());
    }

    @Test
    void finalizeIfStillDeleting_whenMissing_doesNothing() {
        UUID id = UUID.randomUUID();
        when(organizationRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(finalizer.finalizeIfStillDeleting(id)).isFalse();
        verify(eventProducer, never()).publishOrganizationDeleted(any());
    }
}
