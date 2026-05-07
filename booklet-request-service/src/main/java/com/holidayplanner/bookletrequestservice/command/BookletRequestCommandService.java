package com.holidayplanner.bookletrequestservice.command;

import com.holidayplanner.bookletrequestservice.dto.BookletRequestCommandResponse;
import com.holidayplanner.bookletrequestservice.dto.CreateBookletRequestCommand;
import com.holidayplanner.bookletrequestservice.event.BookletRequestEvent;
import com.holidayplanner.bookletrequestservice.event.BookletRequestEventType;
import com.holidayplanner.bookletrequestservice.exception.BookletRequestNotFoundException;
import com.holidayplanner.bookletrequestservice.exception.InvalidBookletRequestTransitionException;
import com.holidayplanner.bookletrequestservice.model.BookletRequest;
import com.holidayplanner.bookletrequestservice.model.BookletRequestStatus;
import com.holidayplanner.bookletrequestservice.projection.BookletRequestProjector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class BookletRequestCommandService {

    private final Map<UUID, BookletRequest> commandStore = new ConcurrentHashMap<>();
    private final BookletRequestProjector projector;

    public BookletRequestCommandResponse create(CreateBookletRequestCommand command) {
        LocalDateTime now = LocalDateTime.now();
        BookletRequest request = new BookletRequest(
                UUID.randomUUID(),
                command.organizationId(),
                command.requestedCopies(),
                BookletRequestStatus.REQUESTED,
                command.note(),
                now,
                now);

        commandStore.put(request.id(), request);
        publish(eventFor(BookletRequestEventType.CREATED, request, now));
        return BookletRequestCommandResponse.from(request);
    }

    public BookletRequestCommandResponse markPrinted(UUID requestId) {
        return transition(requestId, BookletRequestStatus.PRINTED, BookletRequestEventType.PRINTED);
    }

    public BookletRequestCommandResponse markDistributed(UUID requestId) {
        return transition(requestId, BookletRequestStatus.DISTRIBUTED, BookletRequestEventType.DISTRIBUTED);
    }

    public BookletRequestCommandResponse cancel(UUID requestId) {
        return transition(requestId, BookletRequestStatus.CANCELLED, BookletRequestEventType.CANCELLED);
    }

    private BookletRequestCommandResponse transition(UUID requestId, BookletRequestStatus targetStatus,
                                                     BookletRequestEventType eventType) {
        BookletRequest current = commandStore.get(requestId);
        if (current == null) {
            throw new BookletRequestNotFoundException(requestId);
        }
        if (!canTransition(current.status(), targetStatus)) {
            throw new InvalidBookletRequestTransitionException(requestId, current.status(), targetStatus);
        }

        LocalDateTime now = LocalDateTime.now();
        BookletRequest updated = current.withStatus(targetStatus, now);
        commandStore.put(requestId, updated);
        publish(eventFor(eventType, updated, now));
        return BookletRequestCommandResponse.from(updated);
    }

    private boolean canTransition(BookletRequestStatus currentStatus, BookletRequestStatus targetStatus) {
        return switch (targetStatus) {
            case PRINTED -> currentStatus == BookletRequestStatus.REQUESTED;
            case DISTRIBUTED -> currentStatus == BookletRequestStatus.PRINTED;
            case CANCELLED -> currentStatus == BookletRequestStatus.REQUESTED
                    || currentStatus == BookletRequestStatus.PRINTED;
            case REQUESTED -> false;
        };
    }

    private BookletRequestEvent eventFor(BookletRequestEventType eventType, BookletRequest request,
                                         LocalDateTime occurredAt) {
        return new BookletRequestEvent(
                UUID.randomUUID(),
                eventType,
                request.id(),
                request.organizationId(),
                request.requestedCopies(),
                request.status(),
                request.note(),
                occurredAt);
    }

    private void publish(BookletRequestEvent event) {
        projector.handle(event);
    }
}
