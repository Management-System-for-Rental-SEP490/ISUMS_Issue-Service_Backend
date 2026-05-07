package com.isums.issueservice.infrastructures.abstracts;

import com.isums.issueservice.domains.dtos.AnswerRequest;
import com.isums.issueservice.domains.dtos.IssueResponseDto;

import java.util.List;
import java.util.UUID;

public interface IssueResponseService {
    IssueResponseDto answer(UUID ticketId, String staffId, AnswerRequest req);
    List<IssueResponseDto> getAll(String locale);
    IssueResponseDto getByTicketId(UUID ticketId, String locale);
    IssueResponseDto getById(UUID Id, String locale);
}
