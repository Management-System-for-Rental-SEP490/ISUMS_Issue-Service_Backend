package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.dtos.IssueExecutionDto;
import com.isums.issueservice.domains.entities.IssueExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssueExecutionRepository extends JpaRepository<IssueExecution, UUID> {
    List<IssueExecution> findByIssueIdOrderByCreatedAtAsc(UUID issueId);
}
