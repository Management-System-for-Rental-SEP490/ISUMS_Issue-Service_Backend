package com.isums.issueservice.infrastructures.abstracts;

import com.isums.issueservice.domains.dtos.CreateExecutionRequest;
import com.isums.issueservice.domains.dtos.IssueExecutionDto;
import com.isums.issueservice.domains.dtos.IssueQuoteDto;
import com.isums.issueservice.domains.entities.IssueExecution;

import java.util.List;
import java.util.UUID;

public interface IssueExecutionService {
    IssueExecutionDto createExecution(UUID issueId, UUID staffId, CreateExecutionRequest req);
    List<IssueExecutionDto> getAll();
    IssueExecutionDto getById(UUID id);
    List<IssueExecutionDto> getByTicketId(UUID ticketId);
}
