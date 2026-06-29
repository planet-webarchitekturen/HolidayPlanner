package com.holidayplanner.organizationservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrganizationOutboxEventRepository extends JpaRepository<OrganizationOutboxEvent, UUID> {

    /** Fetch the oldest unpublished events, capped to a batch size to bound the work per relay tick. */
    List<OrganizationOutboxEvent> findTop100ByProcessedFalseOrderByCreatedAtAsc();
}
