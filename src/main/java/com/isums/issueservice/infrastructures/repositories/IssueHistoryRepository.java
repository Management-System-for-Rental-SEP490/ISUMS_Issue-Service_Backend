package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.IssueHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssueHistoryRepository extends JpaRepository<IssueHistory, UUID> {
    List<IssueHistory> findByIssueTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
