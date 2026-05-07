package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.IssueQuote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssueQuoteRepository extends JpaRepository<IssueQuote, UUID> {
    @Override
    @EntityGraph(attributePaths = {"items", "items.bannerVersion", "items.bannerVersion.banner", "issueTicket"})
    Optional<IssueQuote> findById(UUID id);

    @EntityGraph(attributePaths = {"items", "items.bannerVersion", "items.bannerVersion.banner", "issueTicket"})
    List<IssueQuote> findByIssueTicketIdOrderByCreatedAtDesc(UUID ticketId);

    List<IssueQuote> findByIssueTicketIdInOrderByCreatedAtDesc(List<UUID> ticketIds);
}
