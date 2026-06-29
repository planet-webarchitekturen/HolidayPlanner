package com.holidayplanner.organizationservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls the transactional outbox and publishes pending events to Kafka on a fixed schedule. Each
 * event is only marked processed after the broker acknowledges it ({@code .get()} blocks on the send
 * result), giving at-least-once delivery. On the first failure we stop the batch to preserve
 * per-aggregate ordering; the remaining rows are retried on the next tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationOutboxRelay {

    private final OrganizationOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OrganizationOutboxEvent> pending =
                outboxEventRepository.findTop100ByProcessedFalseOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        for (OrganizationOutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload()).get();
                event.setProcessed(true);
                event.setProcessedAt(Instant.now());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while publishing outbox event {} ({})",
                        event.getId(), event.getEventType(), e);
                break;
            } catch (Exception e) {
                log.error("Failed to publish outbox event {} ({}); will retry next tick",
                        event.getId(), event.getEventType(), e);
                break;
            }
        }
        // Managed entities: the processed flags flush on transaction commit.
    }
}
