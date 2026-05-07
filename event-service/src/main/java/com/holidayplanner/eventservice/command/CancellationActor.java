package com.holidayplanner.eventservice.command;

/**
 * Who initiated cancellation of an event term (mirrors {@code EventTermCancelledPayload.cancelledBy}).
 */
public enum CancellationActor {
    EVENT_OWNER,
    SYSTEM
}
