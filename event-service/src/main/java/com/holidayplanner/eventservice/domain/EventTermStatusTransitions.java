package com.holidayplanner.eventservice.domain;

import com.holidayplanner.eventservice.domain.exception.InvalidStatusTransitionException;
import com.holidayplanner.shared.model.EventTermStatus;

/**
 * Pure domain rules for event-term lifecycle (no Spring).
 */
public final class EventTermStatusTransitions {

    private EventTermStatusTransitions() {
    }

    public static void requireTransition(EventTermStatus from, EventTermStatus to) {
        if (from == to) {
            return;
        }
        boolean allowed = switch (from) {
            case DRAFT -> to == EventTermStatus.ACTIVE || to == EventTermStatus.CANCELLED;
            case ACTIVE -> to == EventTermStatus.CANCELLED;
            case CANCELLED -> false;
        };
        if (!allowed) {
            throw new InvalidStatusTransitionException(from, to);
        }
    }
}
