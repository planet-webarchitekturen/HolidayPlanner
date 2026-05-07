package com.holidayplanner.bookletrequestservice.exception;

import com.holidayplanner.bookletrequestservice.model.BookletRequestStatus;

import java.util.UUID;

public class InvalidBookletRequestTransitionException extends RuntimeException {

    public InvalidBookletRequestTransitionException(UUID requestId, BookletRequestStatus currentStatus,
                                                    BookletRequestStatus targetStatus) {
        super("Cannot change booklet request " + requestId + " from "
                + currentStatus + " to " + targetStatus);
    }
}
