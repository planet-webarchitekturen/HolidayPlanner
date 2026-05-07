package com.holidayplanner.bookletrequestservice.projection;

import com.holidayplanner.bookletrequestservice.dto.BookletRequestDetailResponse;
import com.holidayplanner.bookletrequestservice.dto.BookletRequestSummaryResponse;
import com.holidayplanner.bookletrequestservice.event.BookletRequestEvent;
import com.holidayplanner.bookletrequestservice.model.BookletRequestStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BookletRequestProjection {

    private final Map<UUID, BookletRequestDetailResponse> requests = new ConcurrentHashMap<>();
    private final Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();

    public void apply(BookletRequestEvent event) {
        if (!processedEventIds.add(event.eventId())) {
            return;
        }

        BookletRequestDetailResponse existing = requests.get(event.requestId());
        requests.put(event.requestId(), new BookletRequestDetailResponse(
                event.requestId(),
                event.organizationId(),
                event.requestedCopies(),
                event.status(),
                event.note(),
                existing != null ? existing.createdAt() : event.occurredAt(),
                event.occurredAt()));
    }

    public Optional<BookletRequestDetailResponse> findById(UUID requestId) {
        return Optional.ofNullable(requests.get(requestId));
    }

    public List<BookletRequestDetailResponse> findByOrganizationId(UUID organizationId) {
        return requests.values().stream()
                .filter(request -> request.organizationId().equals(organizationId))
                .sorted(Comparator.comparing(BookletRequestDetailResponse::createdAt))
                .toList();
    }

    public BookletRequestSummaryResponse summarize(UUID organizationId) {
        Map<BookletRequestStatus, Long> counts = new EnumMap<>(BookletRequestStatus.class);
        for (BookletRequestStatus status : BookletRequestStatus.values()) {
            counts.put(status, 0L);
        }

        int totalCopies = 0;
        for (BookletRequestDetailResponse request : findByOrganizationId(organizationId)) {
            counts.compute(request.status(), (key, value) -> value == null ? 1 : value + 1);
            totalCopies += request.requestedCopies();
        }

        return new BookletRequestSummaryResponse(
                organizationId,
                counts.get(BookletRequestStatus.REQUESTED),
                counts.get(BookletRequestStatus.PRINTED),
                counts.get(BookletRequestStatus.DISTRIBUTED),
                counts.get(BookletRequestStatus.CANCELLED),
                totalCopies);
    }
}
