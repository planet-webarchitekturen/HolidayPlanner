package com.holidayplanner.eventservice.repository;

import com.holidayplanner.shared.model.EventTerm;
import com.holidayplanner.shared.model.EventTermStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventTermRepository extends JpaRepository<EventTerm, UUID> {

    List<EventTerm> findByEvent_Id(UUID eventId);

    List<EventTerm> findByStatus(EventTermStatus status);

    @Query("SELECT t FROM EventTerm t JOIN FETCH t.event WHERE t.id = :id")
    Optional<EventTerm> findByIdWithEvent(@Param("id") UUID id);

    /**
     * ACTIVE terms whose start is strictly after {@code fromExclusive} and on/before {@code toInclusive}.
     */
    @Query("SELECT DISTINCT t FROM EventTerm t JOIN FETCH t.event e WHERE t.status = 'ACTIVE' "
            + "AND t.startDateTime > :fromExclusive AND t.startDateTime <= :toInclusive")
    List<EventTerm> findActiveTermsStartingInWindow(
            @Param("fromExclusive") LocalDateTime fromExclusive,
            @Param("toInclusive") LocalDateTime toInclusive);

    /**
     * ACTIVE terms whose start falls in {@code [startInclusive, endExclusive)}.
     */
    @Query("SELECT DISTINCT t FROM EventTerm t JOIN FETCH t.event e WHERE t.status = 'ACTIVE' "
            + "AND t.startDateTime >= :startInclusive AND t.startDateTime < :endExclusive")
    List<EventTerm> findActiveTermsStartingBetween(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);
}
