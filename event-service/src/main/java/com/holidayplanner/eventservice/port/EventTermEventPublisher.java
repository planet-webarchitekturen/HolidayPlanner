package com.holidayplanner.eventservice.port;

import com.holidayplanner.shared.kafka.payload.CapacityIncreasedPayload;
import com.holidayplanner.shared.kafka.payload.EventTermCancelledPayload;
import com.holidayplanner.shared.kafka.payload.ParticipantListRequestedPayload;

/**
 * Outbound port for publishing event-service domain events to Kafka.
 */
public interface EventTermEventPublisher {

    void publishEventTermCancelled(EventTermCancelledPayload payload);

    void publishParticipantListRequested(ParticipantListRequestedPayload payload);

    void publishCapacityIncreased(CapacityIncreasedPayload payload);
}
