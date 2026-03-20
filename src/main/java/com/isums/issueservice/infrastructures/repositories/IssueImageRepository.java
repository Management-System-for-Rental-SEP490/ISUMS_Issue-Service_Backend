package com.isums.issueservice.infrastructures.repositories;

import com.isums.issueservice.domains.entities.IssueImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssueImageRepository extends JpaRepository<IssueImage, UUID> {
    List<IssueImage> findByIssueTicketId(UUID ticketId);
}
