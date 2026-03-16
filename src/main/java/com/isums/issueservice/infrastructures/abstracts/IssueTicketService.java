package com.isums.issueservice.infrastructures.abstracts;

import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueTicketDto;

import java.util.List;
import java.util.UUID;

public interface IssueTicketService {
    IssueTicketDto createIssue(UUID tenantId, CreateIssueRequest request);
    List<IssueTicketDto> getTenantIssues(UUID tenantId);
    IssueTicketDto getIssueById(UUID id);
    List<IssueTicketDto> getAll();
}
