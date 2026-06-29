package com.holidayplanner.shared.model;

public enum PaymentStatus {
    PENDING,
    PAID,
    /** PENDING payment cancelled before the refund pivot — no money was ever taken; compensatable back to PENDING. */
    VOIDED,
    REFUNDED
}
