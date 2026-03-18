package com.isums.issueservice.infrastructures.abstracts;

import com.isums.issueservice.domains.dtos.CreateIssueRequest;
import com.isums.issueservice.domains.dtos.IssueTicketDto;
import com.isums.issueservice.domains.enums.IssueStatus;
import com.isums.issueservice.domains.events.JobEvent;

import java.util.List;
import java.util.UUID;

public interface IssueTicketService {
    IssueTicketDto createIssue(UUID tenantId, CreateIssueRequest request);
    List<IssueTicketDto> getTenantIssues(UUID tenantId);
    IssueTicketDto getIssueById(UUID id);
    List<IssueTicketDto> getAll();
    IssueTicketDto updateStatus(UUID id , IssueStatus newStatus);
    void markScheduled(JobEvent event);
}
