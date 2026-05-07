package com.holidayplanner.bookletrequestservice.query;

import com.holidayplanner.bookletrequestservice.dto.BookletRequestDetailResponse;
import com.holidayplanner.bookletrequestservice.dto.BookletRequestSummaryResponse;
import com.holidayplanner.bookletrequestservice.exception.BookletRequestNotFoundException;
import com.holidayplanner.bookletrequestservice.projection.BookletRequestProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookletRequestQueryService {

    private final BookletRequestProjection projection;

    public BookletRequestDetailResponse getRequest(UUID requestId) {
        return projection.findById(requestId)
                .orElseThrow(() -> new BookletRequestNotFoundException(requestId));
    }

    public List<BookletRequestDetailResponse> getRequestsByOrganization(UUID organizationId) {
        return projection.findByOrganizationId(organizationId);
    }

    public BookletRequestSummaryResponse getSummary(UUID organizationId) {
        return projection.summarize(organizationId);
    }
}
