package com.holidayplanner.identityservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetch the oldest unpublished events, capped to a batch size to bound the
     * work done per relay tick.
     */
    List<OutboxEvent> findTop100ByProcessedFalseOrderByCreatedAtAsc();
}
