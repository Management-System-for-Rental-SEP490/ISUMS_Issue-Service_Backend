package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.IssueQuote;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IssueQuoteRepository extends JpaRepository<IssueQuote, UUID> {
    List<IssueQuote> findByIssueTicketIdOrderByCreatedAtDesc(UUID ticketId);

}
