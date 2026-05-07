package com.isums.issueservice.infrastructures.abstracts;

import com.isums.issueservice.domains.dtos.CreateExecutionRequest;
import com.isums.issueservice.domains.dtos.IssueExecutionDto;
import com.isums.issueservice.domains.dtos.IssueQuoteDto;
import com.isums.issueservice.domains.entities.IssueExecution;

import java.util.List;
import java.util.UUID;

public interface IssueExecutionService {
    IssueExecutionDto createExecution(UUID issueId, String staffId, CreateExecutionRequest req);
    List<IssueExecutionDto> getAll(String locale);
    IssueExecutionDto getById(UUID id, String locale);
    List<IssueExecutionDto> getByTicketId(UUID ticketId, String locale);
}
