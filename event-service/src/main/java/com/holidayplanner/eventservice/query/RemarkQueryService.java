package com.holidayplanner.eventservice.query;

import com.holidayplanner.eventservice.dto.RemarkResponse;
import com.holidayplanner.eventservice.repository.RemarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RemarkQueryService {

    private final RemarkRepository remarkRepository;

    public List<RemarkResponse> getRemarks(UUID eventTermId) {
        return remarkRepository.findByEventTermId(eventTermId).stream()
                .map(RemarkResponse::from)
                .collect(Collectors.toList());
    }
}
