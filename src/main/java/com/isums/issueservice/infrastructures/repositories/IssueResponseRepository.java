package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.IssueResponse;
import com.isums.issueservice.domains.entities.IssueTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IssueResponseRepository extends JpaRepository<IssueResponse, UUID> {
    IssueResponse getIssueResponseByIssueTicket(IssueTicket issueTicket);
}
