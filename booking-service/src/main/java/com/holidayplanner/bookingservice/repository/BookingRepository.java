package com.holidayplanner.bookingservice.repository;

import com.holidayplanner.shared.model.Booking;
import com.holidayplanner.shared.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    // SpringData JPA generates the implementation based on method names
    List<Booking> findByEventTermId(UUID eventTermId);

    List<Booking> findByEventTermIdAndStatus(UUID eventTermId, BookingStatus status);

    // FIFO waitlist order: oldest booking is promoted first.
    List<Booking> findByEventTermIdAndStatusOrderByBookedAtAsc(UUID eventTermId, BookingStatus status);

    List<Booking> findByFamilyMemberId(UUID familyMemberId);

    long countByEventTermIdAndStatus(UUID eventTermId, BookingStatus status);

    long countByFamilyMemberIdAndStatusIn(UUID familyMemberId, Collection<BookingStatus> statuses);

    boolean existsByFamilyMemberIdAndEventTermIdAndStatusIn(
            UUID familyMemberId,
            UUID eventTermId,
            Collection<BookingStatus> statuses);

    @Query("SELECT b FROM Booking b WHERE b.familyMemberId = :familyMemberId AND b.status != 'CANCELLED' ORDER BY b.bookedAt DESC")
    List<Booking> findActiveBookingsByFamilyMember(@Param("familyMemberId") UUID familyMemberId);

    List<Booking> findByEventTermIdAndSagaCancelledTrue(UUID eventTermId);

    // Duplicate-booking guard: an existing non-CANCELLED booking for the same member+term.
    boolean existsByFamilyMemberIdAndEventTermIdAndStatusNot(UUID familyMemberId, UUID eventTermId, BookingStatus status);
}
