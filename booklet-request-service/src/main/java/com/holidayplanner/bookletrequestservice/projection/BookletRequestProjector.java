package com.holidayplanner.bookletrequestservice.projection;

import com.holidayplanner.bookletrequestservice.event.BookletRequestEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookletRequestProjector {

    private final BookletRequestProjection projection;

    public void handle(BookletRequestEvent event) {
        projection.apply(event);
    }
}
