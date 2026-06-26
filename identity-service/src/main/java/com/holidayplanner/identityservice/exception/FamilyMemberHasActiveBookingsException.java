package com.holidayplanner.identityservice.exception;

import java.util.UUID;

/** Thrown when a family member (or a user's family member) still has active bookings and cannot be removed. */
public class FamilyMemberHasActiveBookingsException extends RuntimeException {
    public FamilyMemberHasActiveBookingsException(UUID memberId) {
        super("Cannot remove family member " + memberId + " while they have active bookings. Cancel the bookings first.");
    }
}
