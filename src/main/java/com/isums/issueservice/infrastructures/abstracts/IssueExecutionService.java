package com.isums.issueservice.infrastructures.abstracts;

import com.isums.issueservice.domains.dtos.CreateExecutionRequest;
import com.isums.issueservice.domains.dtos.IssueExecutionDto;

import java.util.UUID;

public interface IssueExecutionService {
    IssueExecutionDto createExecution(UUID issueId, UUID staffId, CreateExecutionRequest req);
}
