package com.holidayplanner.shared.model;

public enum OrganizationStatus {
    ACTIVE,
    DELETING,
    DELETED,
    /** Deletion saga was rolled back before the refund pivot; org is operationally restored to ACTIVE. */
    DELETION_FAILED
}
