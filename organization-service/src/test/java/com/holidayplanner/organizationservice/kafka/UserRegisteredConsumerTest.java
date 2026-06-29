package com.holidayplanner.organizationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.organizationservice.query.OrganizationQueryService;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import com.holidayplanner.shared.kafka.payload.UserRegisteredPayload;
import com.holidayplanner.shared.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the UserRegistered listener: it checks whether the user's organization is known and
 * tolerates both known and unknown organizations without throwing.
 */
@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private OrganizationQueryService queryService;

    private UserRegisteredConsumer consumer() {
        return new UserRegisteredConsumer(queryService, objectMapper);
    }

    private String userRegisteredMessage(UUID organizationId) throws Exception {
        UserRegisteredPayload payload = new UserRegisteredPayload(
                UUID.randomUUID(), "new@user.test", "+430000", organizationId, UserRole.USER);
        KafkaEnvelope<UserRegisteredPayload> envelope = new KafkaEnvelope<>(
                "UserRegistered", "1", "2026-06-30T10:00:00", "identity-service", payload);
        return objectMapper.writeValueAsString(envelope);
    }

    @Test
    void consume_whenOrganizationKnown_checksExistence() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(queryService.organizationExists(orgId)).thenReturn(true);

        consumer().consume(userRegisteredMessage(orgId));

        verify(queryService).organizationExists(orgId);
    }

    @Test
    void consume_whenOrganizationUnknown_isTolerated() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(queryService.organizationExists(orgId)).thenReturn(false);

        consumer().consume(userRegisteredMessage(orgId));

        verify(queryService).organizationExists(orgId);
    }
}
