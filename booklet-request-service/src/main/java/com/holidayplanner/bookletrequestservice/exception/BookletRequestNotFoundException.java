package com.holidayplanner.bookletrequestservice.exception;

import java.util.UUID;

public class BookletRequestNotFoundException extends RuntimeException {

    public BookletRequestNotFoundException(UUID requestId) {
        super("Booklet request not found: " + requestId);
    }
}
