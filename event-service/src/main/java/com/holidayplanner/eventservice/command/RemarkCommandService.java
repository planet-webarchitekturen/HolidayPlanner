package com.holidayplanner.eventservice.command;

import com.holidayplanner.eventservice.domain.exception.EventTermNotFoundException;
import com.holidayplanner.eventservice.dto.CreateRemarkRequest;
import com.holidayplanner.eventservice.dto.RemarkResponse;
import com.holidayplanner.eventservice.repository.EventTermRepository;
import com.holidayplanner.eventservice.repository.RemarkRepository;
import com.holidayplanner.shared.model.Remark;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RemarkCommandService {

    private final EventTermRepository eventTermRepository;
    private final RemarkRepository remarkRepository;

    public RemarkResponse createRemark(UUID eventTermId, CreateRemarkRequest request) {
        eventTermRepository.findById(eventTermId)
                .orElseThrow(() -> new EventTermNotFoundException(eventTermId));
        Remark remark = new Remark();
        remark.setEventTermId(eventTermId);
        remark.setFamilyMemberId(request.getFamilyMemberId());
        remark.setEventOwnerId(request.getEventOwnerId());
        remark.setDescription(request.getDescription());
        Remark saved = remarkRepository.save(remark);
        return RemarkResponse.from(saved);
    }
}
